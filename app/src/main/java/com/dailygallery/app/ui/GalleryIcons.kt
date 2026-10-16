package com.dailygallery.app.ui

import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector

/** The two tab icons the core icon set lacks, drawn on Material's 24-unit grid. */
object GalleryIcons {
    /** A framed landscape, like the launcher icon: today's artwork. */
    val Frame: ImageVector by lazy {
        materialIcon("Frame") {
            materialPath(pathFillType = PathFillType.EvenOdd) {
                moveTo(3f, 4f); horizontalLineToRelative(18f); verticalLineToRelative(16f); horizontalLineTo(3f); close()
                moveTo(5f, 6f); verticalLineToRelative(12f); horizontalLineToRelative(14f); verticalLineTo(6f); close()
            }
            materialPath {
                moveTo(6f, 17f); lineToRelative(3.5f, -4.5f); lineToRelative(2.5f, 3f); lineToRelative(2f, -2.5f); lineToRelative(3.5f, 4f); close()
                moveTo(15.5f, 9.5f); arcToRelative(1.5f, 1.5f, 0f, true, true, -0.01f, 0f); close()
            }
        }
    }

    /** Four tiles: the gallery of past days. */
    val Grid: ImageVector by lazy {
        materialIcon("Grid") {
            materialPath {
                moveTo(4f, 4f); horizontalLineToRelative(7f); verticalLineToRelative(7f); horizontalLineTo(4f); close()
                moveTo(13f, 4f); horizontalLineToRelative(7f); verticalLineToRelative(7f); horizontalLineToRelative(-7f); close()
                moveTo(4f, 13f); horizontalLineToRelative(7f); verticalLineToRelative(7f); horizontalLineTo(4f); close()
                moveTo(13f, 13f); horizontalLineToRelative(7f); verticalLineToRelative(7f); horizontalLineToRelative(-7f); close()
            }
        }
    }
}
