package com.application.zaona.weather

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

class SponsorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleweathersyncerngTheme {
                val topBarState = rememberTopAppBarState()
                val scrollBehavior = MiuixScrollBehavior(state = topBarState)
                val context = LocalContext.current
                
                // Mock sponsors data
                val sponsors = listOf(
                    "Sponsor 1",
                    "Sponsor 2",
                    "Sponsor 3"
                )

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = "赞助者鸣谢",
                            navigationIcon = {
                                IconButton(
                                    modifier = Modifier.padding(start = 12.dp),
                                    onClick = { finish() }
                                ) {
                                    Icon(
                                        imageVector = MiuixIcons.Back,
                                        contentDescription = "返回"
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior
                        )
                    }
                ) { innerPadding ->
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = innerPadding
                    ) {
                        item {
                            Button(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .padding(top = 16.dp, bottom = 16.dp),
                                onClick = {
                                    // Open sponsor link
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://afdian.com/a/zaona"))
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColorsPrimary()
                            ) {
                                Text("前往赞助", color = Color.White)
                            }
                        }

                        item {
                            Card(
                                modifier = Modifier.padding(horizontal = 16.dp)
                            ) {
                                sponsors.forEach { sponsor ->
                                    BasicComponent(
                                        title = sponsor,
                                        onClick = { /* Optional: Link to sponsor profile */ }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
