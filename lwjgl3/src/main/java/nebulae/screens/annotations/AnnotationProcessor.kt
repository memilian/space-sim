package nebulae.screens.annotations

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.scenes.scene2d.utils.Drawable
import com.badlogic.gdx.utils.Align
import com.badlogic.gdx.utils.Array
import com.kotcrab.vis.ui.VisUI
import com.kotcrab.vis.ui.util.adapter.SimpleListAdapter
import com.kotcrab.vis.ui.widget.ListView
import com.kotcrab.vis.ui.widget.VisTable
import com.kotcrab.vis.ui.widget.color.ColorPickerAdapter
import ktx.actors.onChange
import ktx.vis.KVisTable
import ktx.vis.addTextTooltip
import nebulae.generation.Settings
import java.lang.reflect.Field
import kotlin.reflect.KMutableProperty
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.kotlinProperty


class AnnotationProcessor(private val table: KVisTable, private val settings: Settings.Companion) {

    fun process() {
        table.apply {
            for (field in settings::class.memberProperties) {
                processField(field.javaField!!, settings)
            }
        }
    }

    private fun KVisTable.processField(field: Field, owner: Any) {
        val name = if (field.isAnnotationPresent(WithName::class.java))
            field.getAnnotation(WithName::class.java).name
        else field.name
        val description = if (field.isAnnotationPresent(WithDescription::class.java))
            field.getAnnotation(WithDescription::class.java).description
        else ""

        if (field.isAnnotationPresent(Tab::class.java)) {
            label(field.name) {
                it.colspan(3)
                color = Color.SKY
            }
            row()
            separator {
                it.colspan(3)
                it.width(300f)
                color = Color.BLACK
                it.padBottom(8f)
            }
            row()
            for (f in field.type.declaredFields) {
                val newOwner: Any = when (field.kotlinProperty) {
                    null -> {
                        field.trySetAccessible()
                        field.get(owner)
                    }
                    else -> field.kotlinProperty!!.getter.call(owner)!!
                }
                processField(f, newOwner)
            }
            separator {
                it.colspan(3)
                it.width(300f)
                it.padTop(8f)
                color = Color.BLACK
            }
            row()
            return
        }

        if (field.kotlinProperty !is KMutableProperty<*>) {
            throw IllegalStateException("Property ${field.name} needs to be mutable")
        }

        if (field.isAnnotationPresent(Range::class.java)) {
            val property = field.kotlinProperty!! as KMutableProperty<*>
            val range = field.getAnnotation(Range::class.java)
            label(name) {
                it.align(Align.left)
                if (description.isNotEmpty()) {
                    addTextTooltip(description)
                }
            }
            val slider = slider(range.from, range.to, range.step)
            val valueLabel = label(property.call(owner).toString()) {
                this.setAlignment(Align.right)
                it.align(Align.right).width(75f).expandX()
            }
            slider.value = (property.getter.call(owner) as Number).toFloat()
            slider.onChange {
                when (property.returnType.classifier) {
                    Float::class -> property.setter.call(owner, (slider.value * 100).toInt() / 100f)
                    Int::class -> property.setter.call(owner, slider.value.toInt())
                }
                valueLabel.setText(property.getter.call(owner).toString())
            }
        }
        if (field.isAnnotationPresent(Check::class.java)) {
            val property = field.kotlinProperty!! as KMutableProperty<*>
            val checkbox = checkBox(name) {
                it.colspan(3)
                it.align(Align.left)
            }
            if (description.isNotEmpty()) {
                checkbox.addTextTooltip(description)
            }
            checkbox.isChecked = property.getter.call(owner) as Boolean
            checkbox.onChange {
                property.setter.call(owner, checkbox.isChecked)
            }
        }
        if (field.isAnnotationPresent(ColorPicker::class.java)) {
            val property = field.kotlinProperty!! as KMutableProperty<*>
            label(field.name) {
                it.colspan(3)
                color = Color.SKY
            }
            row()
            val picker = basicColorPicker() {
                it.colspan(3)
                it.align(Align.center)
            }
            if (description.isNotEmpty()) {
                picker.addTextTooltip(description)
            }
            picker.color = property.getter.call(owner) as Color
            picker.listener = object : ColorPickerAdapter() {
                override fun changed(newColor: Color?) {
                    property.setter.call(owner, newColor)
                }
            }
        }
        if (field.isAnnotationPresent(EnumList::class.java)) {
            val property = field.kotlinProperty!! as KMutableProperty<*>
            val enumValue = (property.getter.call(owner))!!
            val values = enumValue::class.members.first { it.name == "values" }.call() as kotlin.Array<*>
            val arr: Array<Any> = Array<Any>(values.size)
            for (item in values) {
                arr.add(item)
            }
            val adapter = object : SimpleListAdapter<Any>(arr) {
                private val selection: Drawable = VisUI.getSkin().getDrawable("list-selection")
                private val bg = VisUI.getSkin().getDrawable("window-bg")

                init {
                    selectionMode = SelectionMode.SINGLE;
                }

                override fun selectView(view: VisTable) {
                    view.background = selection
                }

                override fun deselectView(view: VisTable) {
                    view.background = bg
                    stage.scrollFocus = null;
                }
            }
            val view = listView(adapter) {
                setItemClickListener {
                    table.stage.unfocusAll()
                    property.setter.call(owner, it)
                }
            }
            adapter.selectionManager.select(property.getter.call(owner))
            view.updatePolicy = ListView.UpdatePolicy.ON_DRAW;
            add(view.mainTable).colspan(2)
        }
        row()
    }

}