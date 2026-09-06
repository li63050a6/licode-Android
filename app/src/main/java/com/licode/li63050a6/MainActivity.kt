package com.licode.li63050a6

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import com.licode.li63050a6.ui.navigation.AppNav
import com.licode.li63050a6.ui.theme.LicodeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LicodeTheme {
                AppNav()
            }
        }
    }
}