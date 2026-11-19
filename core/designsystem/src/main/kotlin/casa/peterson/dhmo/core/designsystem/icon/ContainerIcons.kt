package casa.peterson.dhmo.core.designsystem.icon

import androidx.annotation.DrawableRes
import casa.peterson.dhmo.core.model.ContainerIcon

/** The inked line drawing for a container: glasses and cups share one, bottles share another. */
@DrawableRes
fun ContainerIcon.drawableRes(): Int = when (this) {
    ContainerIcon.GLASS, ContainerIcon.CUP -> DhmoIcons.Glass
    ContainerIcon.MUG -> DhmoIcons.Mug
    ContainerIcon.BOTTLE, ContainerIcon.LARGE_BOTTLE -> DhmoIcons.Bottle
    ContainerIcon.FLASK -> DhmoIcons.Flask
}
