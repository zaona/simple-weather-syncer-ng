package com.application.zaona.weather

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.application.zaona.weather.model.CityLocation
import com.application.zaona.weather.service.LocationHelper
import com.application.zaona.weather.service.WeatherService
import com.application.zaona.weather.ui.theme.SimpleweathersyncerngTheme
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import top.yukonga.miuix.kmp.basic.TextButton
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.window.WindowDialog

class LocationPickerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleweathersyncerngTheme {
                val topBarState = rememberTopAppBarState()
                val scrollBehavior = MiuixScrollBehavior(state = topBarState)

                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val locationHelper = remember { LocationHelper(context) }
                
                var searchText by remember { mutableStateOf("") }
                var isSearchExpanded by remember { mutableStateOf(false) }
                val searchHistory = remember { mutableStateListOf<CityLocation>() }
                val searchResults = remember { mutableStateListOf<CityLocation>() }
                
                var isPositioning by remember { mutableStateOf(false) }
                val showDialog = remember { mutableStateOf(false) }
                var dialogTitle by remember { mutableStateOf("") }
                var dialogSummary by remember { mutableStateOf("") }
                var dialogSuccess by remember { mutableStateOf(false) }
                var locatedCity by remember { mutableStateOf<CityLocation?>(null) }

                // Load history
                LaunchedEffect(Unit) {
                    searchHistory.clear()
                    searchHistory.addAll(WeatherService.loadRecentSearches(context))
                }
                
                WindowDialog(
                    title = dialogTitle,
                    summary = dialogSummary,
                    show = showDialog.value,
                    onDismissRequest = { showDialog.value = false }
                ) {
                     TextButton(
                        text = "确定",
                        onClick = { 
                            showDialog.value = false
                            if (dialogSuccess && locatedCity != null) {
                                WeatherService.addToRecentSearches(context, locatedCity!!)
                                val intent = Intent().apply { 
                                    putExtra("location", locatedCity.toString())
                                    putExtra("location_data", com.google.gson.Gson().toJson(locatedCity))
                                }
                                (context as? Activity)?.setResult(Activity.RESULT_OK, intent)
                                (context as? Activity)?.finish()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    ) {
                        isPositioning = true
                        scope.launch {
                            try {
                                val location = locationHelper.getCurrentLocation()
                                val city = WeatherService.getCityByCoordinates(context, location.longitude, location.latitude)
                                if (city != null) {
                                    locatedCity = city
                                    dialogTitle = "定位成功"
                                    dialogSummary = "当前位置：${city.name} (${city.adm1} - ${city.adm2})"
                                    dialogSuccess = true
                                } else {
                                    dialogTitle = "定位失败"
                                    dialogSummary = "未找到该位置的城市信息"
                                    dialogSuccess = false
                                }
                            } catch (e: Exception) {
                                dialogTitle = "定位失败"
                                dialogSummary = e.message ?: "未知错误"
                                dialogSuccess = false
                            } finally {
                                isPositioning = false
                                showDialog.value = true
                            }
                        }
                    } else {
                        Toast.makeText(context, "需要位置权限才能定位", Toast.LENGTH_SHORT).show()
                    }
                }

                Scaffold(
                    contentWindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
                    topBar = {
                        TopAppBar(
                            title = "位置设置",
                            navigationIcon = {
                                IconButton(
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
                            .overScrollVertical()
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
                                            if (it.isNotEmpty()) {
                                                scope.launch {
                                                    try {
                                                        val results = WeatherService.searchLocation(context, it)
                                                        searchResults.clear()
                                                        searchResults.addAll(results)
                                                    } catch (e: Exception) {
                                                        // Ignore or show error in log
                                                    }
                                                }
                                            } else {
                                                searchResults.clear()
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
                                                searchResults.clear()
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
                                            if (searchResults.isEmpty()) {
                                                 BasicComponent(title = "搜索中...")
                                            } else {
                                                searchResults.forEach { result ->
                                                    BasicComponent(
                                                        title = result.name,
                                                        summary = "${result.adm1} - ${result.adm2}",
                                                        onClick = {
                                                            WeatherService.addToRecentSearches(context, result)
                                                            val intent = Intent().apply { 
                                                                putExtra("location", result.toString())
                                                                putExtra("location_data", com.google.gson.Gson().toJson(result))
                                                            }
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

                        if (!isSearchExpanded) {
                            item {
                                SmallTitle(text = "当前位置")
                                Card(
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                                ) {
                                    Box(contentAlignment = Alignment.CenterEnd) {
                                        BasicComponent(
                                            title = if (isPositioning) "正在定位..." else "定位当前位置",
                                            summary = if (isPositioning) "请稍候" else "点击获取精确位置",
                                            onClick = {
                                                if (isPositioning) return@BasicComponent
                                                
                                                if (locationHelper.hasPermission()) {
                                                    isPositioning = true
                                                    scope.launch {
                                                        try {
                                                            val location = locationHelper.getCurrentLocation()
                                                            val city = WeatherService.getCityByCoordinates(context, location.longitude, location.latitude)
                                                            if (city != null) {
                                                                locatedCity = city
                                                                dialogTitle = "定位成功"
                                                                dialogSummary = "当前位置：${city.name} (${city.adm1} - ${city.adm2})"
                                                                dialogSuccess = true
                                                            } else {
                                                                dialogTitle = "定位失败"
                                                                dialogSummary = "未找到该位置的城市信息"
                                                                dialogSuccess = false
                                                            }
                                                        } catch (e: Exception) {
                                                            dialogTitle = "定位失败"
                                                            dialogSummary = e.message ?: "未知错误"
                                                            dialogSuccess = false
                                                        } finally {
                                                            isPositioning = false
                                                            showDialog.value = true
                                                        }
                                                    }
                                                } else {
                                                    permissionLauncher.launch(
                                                        arrayOf(
                                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                                        )
                                                    )
                                                }
                                            }
                                        )
                                        if (isPositioning) {
                                            Row {
                                                InfiniteProgressIndicator(modifier = Modifier.width(20.dp))
                                                Spacer(modifier = Modifier.width(16.dp))
                                            }
                                        }
                                    }
                                }
                            }
                            
                            if (searchHistory.isNotEmpty()) {
                                item {
                                    SmallTitle(text = "位置历史")
                                    Card(
                                        modifier = Modifier.padding(horizontal = 16.dp)
                                    ) {
                                        searchHistory.forEach { history ->
                                            BasicComponent(
                                                title = history.name,
                                                summary = "${history.adm1} - ${history.adm2}",
                                                onClick = {
                                                    WeatherService.addToRecentSearches(context, history) // Re-add to move to top
                                                    val intent = Intent().apply { 
                                                        putExtra("location", history.toString())
                                                        putExtra("location_data", com.google.gson.Gson().toJson(history))
                                                    }
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
}

