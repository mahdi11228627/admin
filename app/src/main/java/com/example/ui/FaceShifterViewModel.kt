package com.example.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.R
import com.example.data.AppDatabase
import com.example.data.FaceRepository
import com.example.data.FaceTransformation
import com.example.network.Content
import com.example.network.GenerateContentRequest
import com.example.network.GenerationConfig
import com.example.network.ImageConfig
import com.example.network.InlineData
import com.example.network.Part
import com.example.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

sealed interface FaceUiState {
    object Idle : FaceUiState
    object Loading : FaceUiState
    data class Success(val original: Bitmap, val transformed: Bitmap, val isSimulated: Boolean, val message: String) : FaceUiState
    data class Error(val message: String) : FaceUiState
}

data class FaceEffect(
    val id: String,
    val name: String,
    val enName: String,
    val icon: String,
    val prompt: String,
    val persianDescription: String
)

class FaceShifterViewModel(
    application: Application,
    private val repository: FaceRepository
) : AndroidViewModel(application) {

    private val context = application.applicationContext

    // Available effects defined as constant list
    val effects = listOf(
        FaceEffect("old", "چهره پیر", "Old Age", "👴", 
            "Change this person's face to look around 80 years old. Add realistic facial wrinkles, grey/white hair, and aged skin textures, while preserving key facial identity, posture and background. Output only the modified image.",
            "افکت سفر در زمان (پیری) چهره شما را با حفظ کلیه جزییات به دوران سنین بالا و چین و چروک‌های واقعی شبیه‌سازی می‌کند."),
        
        FaceEffect("child", "چهره کودک", "Childhood", "👶", 
            "Change this person's face to look like an adorable 3-year-old child. Make eyes relatively larger, cheeks rosy and chubby, smooth skin, keeping elements of identity and poses. Output only the modified image.",
            "افکت کودکی چهره را به دوران شیرخوارگی شبیه‌سازی کرده، گونه‌ها را برجسته و پوست را صاف‌تر و چشمان را درشت‌تر می‌کند."),
        
        FaceEffect("bald", "کچل هنری", "Bald Filter", "👨‍🦲", 
            "Modify this person to be completely bald, removing all hair from the top of the head realistically. Retain eyebrows and facial hair shape if any, matching lighting and background. Output only the modified image.",
            "افکت کچل سر را بدون مو بازسازی می‌کند؛ کچل براق هنری و بامزه ویژه شما!"),
        
        FaceEffect("gender_swap", "تغییر جنسیت", "Gender Swap", "🔄", 
            "Swap the gender of the person's face in the image. If male, make female with feminine traits, elegant hair, and smooth makeup features. If female, make male with masculine traits, short hair, and slightly stronger jawline. Keep expressions, pose and background. Output only the modified image.",
            "تغییر ساختار چهره زن به مرد یا مرد به زن با استفاده از الگوهای متناسب استخوانی و آرایشی."),
        
        FaceEffect("anime", "طرح انیمه", "Anime Style", "🌸", 
            "Transform this face into a gorgeous, high-quality, cute Japanese anime artwork style. Keep the face layout, eyes position, and hair color recognizable, but drawn as a beautiful masterwork anime character. Output only the modified image.",
            "تبدیل پرتره شما به نقاشی‌های فاخر کارتونی ژاپنی و آرت انیمه با چشمان درشت و رنگ‌های ملایم."),
        
        FaceEffect("cartoon", "کارتون سه بعدی", "3D Cartoon", "🎬", 
            "Convert this portrait into a cute 3D cartoon style character, like a Disney or Pixar animated movie character, with big expressive eyes, pristine model rendering, soft shadows, and clean features. Output only the modified image.",
            "تغییر چهره به شکل فانتزی انیمیشن‌های سه‌بعدی دیزنی و پیکسار با لعاب متالیک و جذاب."),
        
        FaceEffect("animal", "پت کیوت (حیوان)", "Animal Cute", "🐱", 
            "Incorporate cute animal traits like realistic kitty ears, kitten nose, and whiskers onto this person's face in an adorable fantasy blend, matching lighting perfectly. Output only the modified image.",
            "ترکیب استثنایی چهره شما با عناصر گربه مینیاتوری کیوت، شامل گوش‌های پشمالو و شوارب‌های فانتزی."),
        
        FaceEffect("fantasy", "الوار فانتزی", "Fantasy Elf", "✨", 
            "Transform this portrait into an epic mythical fantasy character class, like an elegant high-elf or noble warrior with magical glowing eyes, subtle fantasy crown, and majestic runic background elements. Output only the modified image.",
            "تبدیل و غوطه‌وری چهره به دنیای تخیلات فانتزی قدیمی به عنوان یک الف اصیل افسانه‌ای یا قهرمان شاهزادگان با تزیینات نوری زیبا.")
    )

    // UI States
    private val _originalBitmap = MutableStateFlow<Bitmap?>(null)
    val originalBitmap = _originalBitmap.asStateFlow()

    private val _selectedEffect = MutableStateFlow<FaceEffect>(effects[0])
    val selectedEffect = _selectedEffect.asStateFlow()

    private val _uiState = MutableStateFlow<FaceUiState>(FaceUiState.Idle)
    val uiState = _uiState.asStateFlow()

    // Database History Flow
    val history: StateFlow<List<FaceTransformation>> = repository.allTransformations
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        // Load default preset man
        loadPreset(R.drawable.img_preset_man)
    }

    fun selectEffect(effect: FaceEffect) {
        _selectedEffect.value = effect
    }

    fun loadPreset(resId: Int) {
        try {
            val bitmap = BitmapFactory.decodeResource(context.resources, resId)
            _originalBitmap.value = bitmap
            _uiState.value = FaceUiState.Idle
        } catch (e: Exception) {
            _uiState.value = FaceUiState.Error("خطا در بارگذاری پیش‌فرض: ${e.localizedMessage}")
        }
    }

    fun setOriginalImage(bitmap: Bitmap) {
        _originalBitmap.value = bitmap
        _uiState.value = FaceUiState.Idle
    }

    fun processUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                if (bitmap != null) {
                    withContext(Dispatchers.Main) {
                        setOriginalImage(bitmap)
                    }
                } else {
                    _uiState.value = FaceUiState.Error("امکان خواندن عکس به عنوان تصویر وجود ندارد.")
                }
            } catch (e: Exception) {
                _uiState.value = FaceUiState.Error("خطا در بارگذاری عکس: ${e.localizedMessage}")
            }
        }
    }

    fun applyAI() {
        val original = _originalBitmap.value
        if (original == null) {
            _uiState.value = FaceUiState.Error("لطفاً ابتدا یک عکس انتخاب کنید.")
            return
        }

        val effect = _selectedEffect.value
        _uiState.value = FaceUiState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            val apiKey = BuildConfig.GEMINI_API_KEY
            val isKeyMissingOrPlaceholder = apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY"

            if (isKeyMissingOrPlaceholder) {
                // Return Simulated Fallback immediately with brief message
                simulateTransformation(original, effect, "به دلیل عدم تنظیم کلید هوش مصنوعی واقعی (Gemini API Key)، افکت با الگوریتم پردازنده محلی چهره‌پرداز شبیه‌سازی شد.")
            } else {
                try {
                    // Try Real Gemini API call
                    val base64Image = original.toBase64()
                    val request = GenerateContentRequest(
                        contents = listOf(
                            Content(
                                parts = listOf(
                                    Part(text = effect.prompt),
                                    Part(inlineData = InlineData(mimeType = "image/jpeg", data = base64Image))
                                )
                            )
                        ),
                        generationConfig = GenerationConfig(
                            responseModalities = listOf("IMAGE"),
                            imageConfig = ImageConfig(aspectRatio = "1:1", imageSize = "1K"),
                            temperature = 0.5f
                        )
                    )

                    // Execute using gemini-2.5-flash-image
                    val response = RetrofitClient.service.generateContent("gemini-2.5-flash-image", apiKey, request)
                    
                    var generatedBitmap: Bitmap? = null
                    // Read base64 image from the first inlineData part of the candidates
                    val part = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()
                    if (part?.inlineData != null) {
                        generatedBitmap = part.inlineData.data.toBitmap()
                    }

                    if (generatedBitmap != null) {
                        saveToHistoryAndSuccess(original, generatedBitmap, effect, false, "ویرایش هوشمند با قدرت مدل هوش مصنوعی Gemini 2.5 با موفقیت روی چهره شما اعمال شد.")
                    } else {
                        // Fallback to simulation if model replied but didn't output an image
                        simulateTransformation(original, effect, "مدل هوش مصنوعی تصویری ارسال نکرد. فرآیند به شبیه‌ساز داخلی چهره هدایت شد.")
                    }

                } catch (e: Exception) {
                    // Fail gracefully to simulation if anything network related raises
                    simulateTransformation(original, effect, "برقراری ارتباط با سرور هویت هوشمند انجام نشد. ویرایش به صورت آفلاین شبیه‌سازی شد. دلیل: ${e.localizedMessage}")
                }
            }
        }
    }

    private suspend fun simulateTransformation(original: Bitmap, effect: FaceEffect, customMessage: String) {
        val simBitmap = applyLocalFilter(original, effect.id)
        saveToHistoryAndSuccess(original, simBitmap, effect, true, customMessage)
    }

    private suspend fun saveToHistoryAndSuccess(
        original: Bitmap,
        transformed: Bitmap,
        effect: FaceEffect,
        isSimulated: Boolean,
        message: String
    ) {
        try {
            // Save files locally to context filesDir
            val origPath = saveBitmapLocally(original, "orig")
            val transPath = saveBitmapLocally(transformed, "trans")

            val item = FaceTransformation(
                effectId = effect.id,
                effectName = effect.name,
                originalPath = origPath,
                transformedPath = transPath
            )

            // Save to database
            repository.insert(item)

            withContext(Dispatchers.Main) {
                _uiState.value = FaceUiState.Success(original, transformed, isSimulated, message)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _uiState.value = FaceUiState.Success(original, transformed, isSimulated, "$message (خطا در ذخیره سابقه: ${e.localizedMessage})")
            }
        }
    }

    private fun saveBitmapLocally(bitmap: Bitmap, prefix: String): String {
        val file = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
        }
        return file.absolutePath
    }

    fun loadFromHistoryItem(item: FaceTransformation) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val origFile = File(item.originalPath)
                val transFile = File(item.transformedPath)
                if (origFile.exists() && transFile.exists()) {
                    val original = BitmapFactory.decodeFile(origFile.absolutePath)
                    val transformed = BitmapFactory.decodeFile(transFile.absolutePath)
                    val matchedEffect = effects.find { it.id == item.effectId } ?: effects[0]

                    withContext(Dispatchers.Main) {
                        _originalBitmap.value = original
                        _selectedEffect.value = matchedEffect
                        _uiState.value = FaceUiState.Success(
                            original = original,
                            transformed = transformed,
                            isSimulated = false,
                            message = "تصویر ویرایش شده از تاریخچه بارگذاری گردید."
                        )
                    }
                } else {
                    _uiState.value = FaceUiState.Error("فایل‌های این سابقه در دستگاه یافت نشدند.")
                }
            } catch (e: Exception) {
                _uiState.value = FaceUiState.Error("خطا در بازیابی سابقه: ${e.localizedMessage}")
            }
        }
    }

    fun deleteHistoryItem(id: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteById(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAll()
        }
    }

    fun shareTransformed(transformed: Bitmap) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Write bitmap to cache directory
                val cachePath = File(context.cacheDir, "images")
                cachePath.mkdirs()
                val stream = FileOutputStream("$cachePath/result_face.png")
                transformed.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.close()

                val newFile = File(cachePath, "result_face.png")
                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    newFile
                )

                if (contentUri != null) {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                        putExtra(Intent.EXTRA_STREAM, contentUri)
                        type = "image/png"
                    }
                    val chooser = Intent.createChooser(shareIntent, "اشتراک‌گذاری چهره پردازش شده").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                }
            } catch (e: Exception) {
                _uiState.value = FaceUiState.Error("کدهای اشتراک‌گذاری با شکست مواجه شد: ${e.localizedMessage}")
            }
        }
    }

    // Helper functions for image manipulation
    private fun Bitmap.toBase64(): String {
        val outputStream = ByteArrayOutputStream()
        this.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun String.toBitmap(): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(this, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun applyLocalFilter(bitmap: Bitmap, effectId: String): Bitmap {
        val result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        val paint = Paint().apply { isAntiAlias = true }

        when (effectId) {
            "old" -> {
                // Apply wrinkling with desaturated styling
                val colorMatrix = ColorMatrix().apply {
                    setSaturation(0.2f)
                }
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(result, 0f, 0f, paint)

                paint.colorFilter = null
                paint.color = Color.argb(85, 50, 40, 35)
                paint.strokeWidth = 3.5f
                paint.style = Paint.Style.STROKE

                val w = result.width.toFloat()
                val h = result.height.toFloat()
                // Forehead wrinkles wrinkles
                canvas.drawLine(w * 0.32f, h * 0.22f, w * 0.68f, h * 0.22f, paint)
                canvas.drawLine(w * 0.28f, h * 0.26f, w * 0.72f, h * 0.26f, paint)
                canvas.drawLine(w * 0.33f, h * 0.30f, w * 0.67f, h * 0.30f, paint)
                // Under eyes wrinkles
                canvas.drawArc(w * 0.24f, h * 0.44f, w * 0.42f, h * 0.49f, 0f, 180f, false, paint)
                canvas.drawArc(w * 0.58f, h * 0.44f, w * 0.76f, h * 0.49f, 0f, 180f, false, paint)
                // Smile folds
                canvas.drawLine(w * 0.39f, h * 0.56f, w * 0.34f, h * 0.68f, paint)
                canvas.drawLine(w * 0.61f, h * 0.56f, w * 0.66f, h * 0.68f, paint)
            }
            "child" -> {
                // Bilinear blur to smooth features + rosy cheeks
                val w = result.width
                val h = result.height
                val scaledDown = Bitmap.createScaledBitmap(result, w / 4, h / 4, true)
                val smoothed = Bitmap.createScaledBitmap(scaledDown, w, h, true)
                canvas.drawBitmap(smoothed, 0f, 0f, paint)

                // Large adorable pink cheeks
                paint.color = Color.argb(90, 255, 110, 140)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(w * 0.30f, h * 0.60f, w * 0.09f, paint)
                canvas.drawCircle(w * 0.70f, h * 0.60f, w * 0.09f, paint)
            }
            "bald" -> {
                val w = result.width.toFloat()
                val h = result.height.toFloat()
                paint.color = Color.argb(225, 240, 201, 175)
                paint.style = Paint.Style.FILL

                // Shaved dome oval
                canvas.drawOval(w * 0.23f, h * 0.04f, w * 0.77f, h * 0.30f, paint)

                // Light highlighting gradient
                val highlight = Paint().apply {
                    isAntiAlias = true
                    shader = LinearGradient(
                        w * 0.5f, h * 0.04f, w * 0.5f, h * 0.30f,
                        Color.argb(245, 248, 220, 195),
                        Color.argb(110, 215, 175, 150),
                        Shader.TileMode.CLAMP
                    )
                }
                canvas.drawOval(w * 0.23f, h * 0.04f, w * 0.77f, h * 0.30f, highlight)
            }
            "gender_swap" -> {
                val w = result.width
                val h = result.height
                // Soft elegant filter tone shift
                val colorMatrix = ColorMatrix().apply {
                    val array = floatArrayOf(
                        1.05f, 0f, 0f, 0f, 12f,
                        0f, 0.96f, 0f, 0f, 4f,
                        0.04f, 0f, 1.15f, 0f, 8f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    set(array)
                }
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(result, 0f, 0f, paint)

                paint.colorFilter = null
                paint.color = Color.argb(130, 20, 15, 25)
                paint.style = Paint.Style.FILL

                // Add stylized soft flowing lock contours for gender shift
                val hairLeft = Path().apply {
                    moveTo(w * 0.20f, h * 0.25f)
                    cubicTo(w * 0.05f, h * 0.45f, w * 0.04f, h * 0.75f, w * 0.22f, h * 1.0f)
                    lineTo(w * 0.02f, h * 1.0f)
                    lineTo(w * 0.05f, h * 0.25f)
                    close()
                }
                canvas.drawPath(hairLeft, paint)

                val hairRight = Path().apply {
                    moveTo(w * 0.80f, h * 0.25f)
                    cubicTo(w * 0.95f, h * 0.45f, w * 0.96f, h * 0.75f, w * 0.78f, h * 1.0f)
                    lineTo(w * 0.98f, h * 1.0f)
                    lineTo(w * 0.95f, h * 0.25f)
                    close()
                }
                canvas.drawPath(hairRight, paint)
            }
            "anime" -> {
                val w = result.width.toFloat()
                val h = result.height.toFloat()
                // Overly vibrant saturated colors
                val colorMatrix = ColorMatrix().apply {
                    setSaturation(1.5f)
                }
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(result, 0f, 0f, paint)

                // Anime anime face lines (blush stripes)
                paint.colorFilter = null
                paint.color = Color.argb(100, 255, 50, 100)
                paint.strokeWidth = 5.5f
                paint.style = Paint.Style.STROKE
                
                canvas.drawLine(w * 0.27f, h * 0.57f, w * 0.33f, h * 0.53f, paint)
                canvas.drawLine(w * 0.30f, h * 0.58f, w * 0.36f, h * 0.54f, paint)

                canvas.drawLine(w * 0.73f, h * 0.57f, w * 0.67f, h * 0.53f, paint)
                canvas.drawLine(w * 0.70f, h * 0.58f, w * 0.64f, h * 0.54f, paint)
            }
            "cartoon" -> {
                val w = result.width.toFloat()
                val h = result.height.toFloat()
                
                // Boost lightness and color clarity
                val colorMatrix = ColorMatrix().apply {
                    setSaturation(1.23f)
                }
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(result, 0f, 0f, paint)

                paint.colorFilter = null
                paint.color = Color.WHITE
                paint.style = Paint.Style.FILL
                
                // Draw majestic bright white highlights in the iris for Pixar feel
                canvas.drawCircle(w * 0.34f, h * 0.43f, w * 0.022f, paint)
                canvas.drawCircle(w * 0.36f, h * 0.45f, w * 0.01f, paint)

                canvas.drawCircle(w * 0.66f, h * 0.43f, w * 0.022f, paint)
                canvas.drawCircle(w * 0.68f, h * 0.45f, w * 0.01f, paint)
            }
            "animal" -> {
                val w = result.width.toFloat()
                val h = result.height.toFloat()

                // Kitty cute pink nose
                paint.color = Color.parseColor("#FFA07A")
                paint.style = Paint.Style.FILL
                val nose = Path().apply {
                    moveTo(w * 0.48f, h * 0.53f)
                    lineTo(w * 0.52f, h * 0.53f)
                    lineTo(w * 0.50f, h * 0.56f)
                    close()
                }
                canvas.drawPath(nose, paint)

                // Face whiskers
                paint.color = Color.argb(125, 230, 245, 255)
                paint.strokeWidth = 3.8f
                paint.style = Paint.Style.STROKE
                
                canvas.drawLine(w * 0.36f, h * 0.55f, w * 0.20f, h * 0.53f, paint)
                canvas.drawLine(w * 0.36f, h * 0.57f, w * 0.18f, h * 0.57f, paint)
                canvas.drawLine(w * 0.36f, h * 0.59f, w * 0.20f, h * 0.61f, paint)

                canvas.drawLine(w * 0.64f, h * 0.55f, w * 0.80f, h * 0.53f, paint)
                canvas.drawLine(w * 0.64f, h * 0.57f, w * 0.82f, h * 0.57f, paint)
                canvas.drawLine(w * 0.64f, h * 0.59f, w * 0.80f, h * 0.61f, paint)

                // Adorable cartoonish ears
                paint.color = Color.parseColor("#2C2C2C")
                paint.style = Paint.Style.FILL
                val earL = Path().apply {
                    moveTo(w * 0.16f, h * 0.22f)
                    lineTo(w * 0.06f, h * 0.08f)
                    lineTo(w * 0.30f, h * 0.16f)
                    close()
                }
                canvas.drawPath(earL, paint)
                
                val earR = Path().apply {
                    moveTo(w * 0.84f, h * 0.22f)
                    lineTo(w * 0.94f, h * 0.08f)
                    lineTo(w * 0.70f, h * 0.16f)
                    close()
                }
                canvas.drawPath(earR, paint)

                // Inner soft pink ears
                paint.color = Color.parseColor("#FFD1DC")
                val earLInner = Path().apply {
                    moveTo(w * 0.17f, h * 0.20f)
                    lineTo(w * 0.09f, h * 0.10f)
                    lineTo(w * 0.27f, h * 0.16f)
                    close()
                }
                canvas.drawPath(earLInner, paint)
                
                val earRInner = Path().apply {
                    moveTo(w * 0.83f, h * 0.20f)
                    lineTo(w * 0.91f, h * 0.10f)
                    lineTo(w * 0.73f, h * 0.16f)
                    close()
                }
                canvas.drawPath(earRInner, paint)
            }
            "fantasy" -> {
                val w = result.width.toFloat()
                val h = result.height.toFloat()

                // Mystical majestic violet gradient glow
                val colorMatrix = ColorMatrix().apply {
                    val array = floatArrayOf(
                        0.85f, 0f, 0.35f, 0f, 25f,
                        0f, 0.88f, 0.15f, 0f, 12f,
                        0.25f, 0f, 1.25f, 0f, 35f,
                        0f, 0f, 0f, 1f, 0f
                    )
                    set(array)
                }
                paint.colorFilter = ColorMatrixColorFilter(colorMatrix)
                canvas.drawBitmap(result, 0f, 0f, paint)

                // Magical magical points
                paint.colorFilter = null
                paint.color = Color.parseColor("#E0FFFF")
                paint.style = Paint.Style.FILL
                
                canvas.drawCircle(w * 0.25f, h * 0.18f, 9f, paint)
                canvas.drawCircle(w * 0.75f, h * 0.16f, 11f, paint)
                canvas.drawCircle(w * 0.18f, h * 0.72f, 7f, paint)
                canvas.drawCircle(w * 0.82f, h * 0.70f, 10f, paint)
            }
        }
        return result
    }
}

class FaceShifterViewModelFactory(
    private val application: Application,
    private val repository: FaceRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FaceShifterViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FaceShifterViewModel(application, repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
