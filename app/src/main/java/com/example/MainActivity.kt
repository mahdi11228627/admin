package com.example

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppDatabase
import com.example.data.FaceRepository
import com.example.data.FaceTransformation
import com.example.ui.FaceEffect
import com.example.ui.FaceShifterViewModel
import com.example.ui.FaceShifterViewModelFactory
import com.example.ui.FaceUiState
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.PurpleAccent
import com.example.ui.theme.TealAccent
import com.example.ui.theme.RoseAccent
import com.example.ui.theme.DarkBg
import com.example.ui.theme.CardSurface
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.BorderLight
import com.example.ui.theme.OverlayBlack
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val database = AppDatabase.getDatabase(applicationContext)
        val repository = FaceRepository(database.faceTransformationDao())
        val factory = FaceShifterViewModelFactory(application, repository)
        val viewModel = ViewModelProvider(this, factory)[FaceShifterViewModel::class.java]

        setContent {
            MyApplicationTheme {
                FaceShifterApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FaceShifterApp(viewModel: FaceShifterViewModel) {
    val context = LocalContext.current
    val originalBitmap by viewModel.originalBitmap.collectAsStateWithLifecycle()
    val selectedEffect by viewModel.selectedEffect.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val historyList by viewModel.history.collectAsStateWithLifecycle()

    // Pick photos from device gallery natively
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.processUri(uri)
            }
        }
    )

    // RTL Persian directionality wrapper
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = DarkBg,
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Top Header Section
                HeaderSection()

                // Main Display (Original Preview, Loading Indicator, or Success Slider)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1.05f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardSurface)
                        .border(1.dp, BorderLight, RoundedCornerShape(24.dp))
                        .testTag("main_display_container"),
                    contentAlignment = Alignment.Center
                ) {
                    when (val state = uiState) {
                        is FaceUiState.Idle -> {
                            if (originalBitmap != null) {
                                Image(
                                    bitmap = originalBitmap!!.asImageBitmap(),
                                    contentDescription = "تصویر اصلی انتخابی",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                // Overlaid quick controls to reload presets
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                                startY = 200f
                                            )
                                        )
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "آماده پردازش هوشمند چهره",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Button(
                                            onClick = { viewModel.loadPreset(R.drawable.img_preset_man) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("👨‍💼 پرتره مرد", color = Color.White, fontSize = 12.sp)
                                            }
                                        }
                                        Button(
                                            onClick = { viewModel.loadPreset(R.drawable.img_preset_woman) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Text("👩‍💼 پرتره زن", color = Color.White, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            } else {
                                Text("هیچ تصویری انتخاب نشده است", color = TextSecondary)
                            }
                        }

                        is FaceUiState.Loading -> {
                            LoadingStateView(selectedEffect.name)
                        }

                        is FaceUiState.Success -> {
                            BeforeAfterSlider(
                                original = state.original,
                                transformed = state.transformed,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        is FaceUiState.Error -> {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = "Error",
                                    tint = RoseAccent,
                                    modifier = Modifier.size(48.dp)
                                )
                                Text(
                                    text = state.message,
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp
                                )
                                Button(
                                    onClick = { viewModel.loadPreset(R.drawable.img_preset_man) },
                                    colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent)
                                ) {
                                    Text("بازنشانی پرتره پیش‌فرض", color = Color.White)
                                }
                            }
                        }
                    }
                }

                // AI Result details summary card (only under Success)
                AnimatedVisibility(
                    visible = uiState is FaceUiState.Success,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    if (uiState is FaceUiState.Success) {
                        val successState = uiState as FaceUiState.Success
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(CardSurface)
                                .padding(16.dp)
                                .border(1.dp, BorderLight, RoundedCornerShape(20.dp)),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(if (successState.isSimulated) RoseAccent else TealAccent)
                                    )
                                    Text(
                                        text = if (successState.isSimulated) "پردازش آفلاین (شبیه‌ساز)" else "پردازش ابری (هوش مصنوعی)",
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(PurpleAccent.copy(alpha = 0.2f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = selectedEffect.name,
                                        color = PurpleAccent,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = successState.message,
                                color = TextSecondary,
                                fontSize = 12.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Interactive Quick Actions Grid (Gallery selection or Process Trigger)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Upload/Chooser button
                    OutlinedButton(
                        onClick = {
                            pickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .testTag("upload_image_button"),
                        border = BorderStroke(1.5.dp, PurpleAccent),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PurpleAccent)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.PhotoLibrary, contentDescription = "آلبوم")
                            Text("انتخاب عکس گالری", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }

                    // Process AI / Apply filter Button
                    Button(
                        onClick = { viewModel.applyAI() },
                        modifier = Modifier
                            .weight(1.2f)
                            .height(54.dp)
                            .testTag("execute_ai_button")
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(16.dp),
                                ambientColor = PurpleAccent,
                                spotColor = PurpleAccent
                            ),
                        colors = ButtonDefaults.buttonColors(containerColor = PurpleAccent),
                        shape = RoundedCornerShape(16.dp),
                        enabled = uiState !is FaceUiState.Loading
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = "اعمال", tint = Color.White)
                            Text("پردازش چهره ✨", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = Color.White)
                        }
                    }
                }

                // Sharing control row (when state is Success)
                AnimatedVisibility(
                    visible = uiState is FaceUiState.Success,
                    enter = fadeIn() + expandVertically()
                ) {
                    if (uiState is FaceUiState.Success) {
                        val successState = uiState as FaceUiState.Success
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.shareTransformed(successState.transformed) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = TealAccent),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Default.Share, contentDescription = "اشتراک گذاری", tint = Color.White)
                                    Text("اشتراک‌گذاری تصویر", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }

                            OutlinedButton(
                                onClick = { viewModel.loadPreset(R.drawable.img_preset_man) },
                                modifier = Modifier
                                    .weight(0.9f)
                                    .height(48.dp),
                                border = BorderStroke(1.dp, TextSecondary.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                            ) {
                                Text("پردازش مجدد", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                // Visual description details of the selected effect
                EffectDetailsCard(selectedEffect)

                // Effects Selection Grid
                Text(
                    text = "افکت‌ها و فیلترهای هوشمند چهره",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .testTag("effects_selection_grid"),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(viewModel.effects) { effect ->
                        val isSelected = effect.id == selectedEffect.id
                        EffectItemCard(
                            effect = effect,
                            isSelected = isSelected,
                            onClick = { viewModel.selectEffect(effect) }
                        )
                    }
                }

                // History Section
                if (historyList.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "سابقه ویرایش‌های اخیر",
                            color = TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(
                            onClick = { viewModel.clearAllHistory() },
                            colors = ButtonDefaults.textButtonColors(contentColor = RoseAccent)
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "پاک کردن همه", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("پاک کردن همه", fontSize = 12.sp)
                        }
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("history_lazy_row")
                    ) {
                        items(historyList) { item ->
                            HistoryThumbnailCard(
                                item = item,
                                onClick = { viewModel.loadFromHistoryItem(item) },
                                onDelete = { viewModel.deleteHistoryItem(item.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderSection() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(TealAccent)
                )
                Text(
                    text = "چهره‌پرداز هوش مصنوعی",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Text(
                text = "افکت‌های فانتزی، سنی و انیمه با پردازش تصویر پیشرفته",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
        Icon(
            imageVector = Icons.Default.Face,
            contentDescription = "Logo icon",
            tint = PurpleAccent,
            modifier = Modifier
                .size(36.dp)
                .background(PurpleAccent.copy(alpha = 0.15f), CircleShape)
                .padding(6.dp)
        )
    }
}

@Composable
fun LoadingStateView(effectName: String) {
    // Dynamic text cycler for futuristic loading feedback
    val phrases = listOf(
        "در حال اجرای پردازش‌های هندسی چهره...",
        "شبیه‌سازی بافت مو و رنگ پوست...",
        "مدل‌سازی المان‌های افکت $effectName...",
        "تعدیل زوایای نوری و فواصل لبخند..."
    )
    var currentWordIndex by remember { mutableStateOf(0) }
    
    // Periodically change the loaded phrase
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2000)
            currentWordIndex = (currentWordIndex + 1) % phrases.size
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp)
    ) {
        CircularProgressIndicator(
            color = PurpleAccent,
            strokeWidth = 4.dp,
            modifier = Modifier.size(48.dp)
        )
        Text(
            text = phrases[currentWordIndex],
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.animateContentSize()
        )
        Text(
            text = "همسان‌سازی لایه افکت $effectName به صورت پیکسل به پیکسل ممکن است کمی زمان‌بر باشد.",
            color = TextSecondary,
            fontSize = 10.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun BeforeAfterSlider(
    original: Bitmap,
    transformed: Bitmap,
    modifier: Modifier = Modifier
) {
    var sliderPosition by remember { mutableStateOf(0.5f) }
    
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    sliderPosition = (change.position.x / size.width).coerceIn(0f, 1f)
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Draw final transformed face as base
            drawImage(
                image = transformed.asImageBitmap(),
                dstSize = IntSize(w.toInt(), h.toInt())
            )

            // Draw original cropped overlay on top
            clipRect(right = w * sliderPosition) {
                drawImage(
                    image = original.asImageBitmap(),
                    dstSize = IntSize(w.toInt(), h.toInt())
                )
            }

            // Draw split beam line
            drawLine(
                color = PurpleAccent,
                start = Offset(w * sliderPosition, 0f),
                end = Offset(w * sliderPosition, h),
                strokeWidth = 6f
            )

            // Visual circular drag handle
            drawCircle(
                color = Color.White,
                center = Offset(w * sliderPosition, h / 2f),
                radius = 24f
            )
            drawCircle(
                color = PurpleAccent,
                center = Offset(w * sliderPosition, h / 2f),
                radius = 16f
            )
        }

        // Before/After visual tag tags
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("قبل از افکت", color = Color.White, fontSize = 10.sp)
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(PurpleAccent.copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp)
        ) {
            Text("بعد از افکت", color = Color.White, fontSize = 10.sp)
        }
    }
}

@Composable
fun EffectDetailsCard(effect: FaceEffect) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CardSurface)
            .padding(12.dp)
            .border(1.dp, BorderLight.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(PurpleAccent.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(effect.icon, fontSize = 24.sp)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "افکت انتخابی: " + effect.name,
                color = TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = effect.persianDescription,
                color = TextSecondary,
                fontSize = 11.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun EffectItemCard(
    effect: FaceEffect,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) PurpleAccent.copy(alpha = 0.12f) else CardSurface)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) PurpleAccent else BorderLight,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(effect.icon, fontSize = 26.sp)
        Text(
            text = effect.name,
            color = if (isSelected) PurpleAccent else TextPrimary,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryThumbnailCard(
    item: FaceTransformation,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val file = File(item.transformedPath)
    
    Box(
        modifier = Modifier
            .width(80.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(CardSurface)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete
            )
            .border(1.dp, BorderLight.copy(alpha = 0.7f), RoundedCornerShape(14.dp))
    ) {
        if (file.exists()) {
            Image(
                bitmap = BitmapFactory.decodeFile(file.absolutePath).asImageBitmap(),
                contentDescription = item.effectName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.BrokenImage, contentDescription = "تصویر نامعتبر", tint = TextSecondary)
            }
        }
        
        // Label indicating effect
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.65f))
                .padding(vertical = 2.dp)
        ) {
            Text(
                text = item.effectName,
                color = Color.White,
                fontSize = 8.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


