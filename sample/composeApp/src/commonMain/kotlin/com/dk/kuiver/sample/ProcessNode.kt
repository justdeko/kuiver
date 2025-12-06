package com.dk.kuiver.sample

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class ProcessNodeType {
    START,
    END,
    PROCESS,
    DECISION,
    SUBPROCESS,
    DATA
}

enum class ProcessIcon(val imageVector: ImageVector) {
    SUN(Icons.Filled.WbSunny),
    FIRE(Icons.Filled.Whatshot),
    COFFEE(Icons.Filled.LocalCafe),
    WATER(Icons.Filled.Opacity),
    CHECKMARK(Icons.Filled.Check),
    EGG(Icons.Filled.Circle),
    PAN(Icons.Filled.Restaurant),
    BREAD(Icons.Filled.FoodBank),
    FOOD(Icons.Filled.Fastfood),
    JUICE(Icons.Filled.LocalBar),
    PLATE(Icons.Filled.Restaurant),
    HERB(Icons.Filled.Eco),
    CHEF(Icons.Filled.RestaurantMenu),
    CELEBRATE(Icons.Filled.Star),
    EYES(Icons.Filled.Visibility),
    BUTTER(Icons.Filled.Fastfood),
    BACON(Icons.Filled.Restaurant),
    ORANGE(Icons.Filled.Circle)
}

data class ProcessNode(
    val title: String,
    val description: String = "",
    val type: ProcessNodeType,
    val icon: ProcessIcon
)
