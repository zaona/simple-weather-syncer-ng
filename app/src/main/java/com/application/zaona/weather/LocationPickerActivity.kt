package com.application.zaona.weather

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberTopAppBarState
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme

class LocationPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleweathersyncerngTheme {
                val topBarState = rememberTopAppBarState()
                val scrollBehavior = MiuixScrollBehavior(state = topBarState)

                val context = LocalContext.current
                
                var searchText by remember { mutableStateOf("") }
                var isSearchExpanded by remember { mutableStateOf(false) }
                val searchHistory = remember { mutableStateListOf("北京", "上海", "广州", "深圳") }
                val searchResults = remember { mutableStateListOf<String>() }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = "位置设置",
                            navigationIcon = {
                                IconButton(
                                    modifier = Modifier.padding(start = 16.dp),
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
                            SearchBar(
                                inputField = {
                                    InputField(
                                        query = searchText,
                                        onQueryChange = { 
                                            searchText = it
                                            searchResults.clear()
                                            if (it.isNotEmpty()) {
                                                // Mock search results
                                                searchResults.add("$it 市")
                                                searchResults.add("$it 县")
                                                searchResults.add("$it 区")
                                            }
                                        },
                                        onSearch = { isSearchExpanded = false },
                                        expanded = isSearchExpanded,
                                        onExpandedChange = { isSearchExpanded = it },
                                        label = "搜索",
                                        leadingIcon = {
                                            Icon(
                                                modifier = Modifier.padding(start = 12.dp, end = 8.dp),
                                                imageVector = MiuixIcons.Search,
                                                contentDescription = "搜索",
                                                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                            )
                                        }
                                    )
                                },
                                expanded = isSearchExpanded,
                                onExpandedChange = { isSearchExpanded = it },
                                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 16.dp, bottom = 8.dp),
                                outsideEndAction = {
                                    Text(
                                        modifier = Modifier
                                            .padding(start = 12.dp, end = 12.dp)
                                            .clickable(
                                                interactionSource = null,
                                                indication = null
                                            ) {
                                                isSearchExpanded = false
                                                searchText = ""
                                            },
                                        text = "取消",
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            ) {
                                Column {
                                    if (searchText.isNotEmpty()) {
                                        Card(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                        ) {
                                            searchResults.forEach { result ->
                                                BasicComponent(
                                                    title = result,
                                                    onClick = {
                                                        if (!searchHistory.contains(result)) {
                                                            searchHistory.add(0, result)
                                                        }
                                                        Toast.makeText(context, "选择了: $result", Toast.LENGTH_SHORT).show()
                                                        val intent = Intent().apply { putExtra("location", result) }
                                                        (context as? Activity)?.setResult(Activity.RESULT_OK, intent)
                                                        (context as? Activity)?.finish()
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (!isSearchExpanded) {
                            item {
                                SmallTitle(text = "当前位置")
                                Card(
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    BasicComponent(
                                        title = "定位当前位置",
                                        summary = "点击获取精确位置",
                                        onClick = {
                                            Toast.makeText(context, "正在获取位置...", Toast.LENGTH_SHORT).show()
                                            // Mock current location
                                            val currentLocation = "当前位置(模拟)"
                                            val intent = Intent().apply { putExtra("location", currentLocation) }
                                            (context as? Activity)?.setResult(Activity.RESULT_OK, intent)
                                            (context as? Activity)?.finish()
                                        }
                                    )
                                }
                            }
                            
                            item {
                                SmallTitle(text = "位置历史")
                                Card(
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                ) {
                                    searchHistory.forEach { history ->
                                        BasicComponent(
                                            title = history,
                                            onClick = {
                                                Toast.makeText(context, "选择了: $history", Toast.LENGTH_SHORT).show()
                                                val intent = Intent().apply { putExtra("location", history) }
                                                (context as? Activity)?.setResult(Activity.RESULT_OK, intent)
                                                (context as? Activity)?.finish()
                                            }
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
}
