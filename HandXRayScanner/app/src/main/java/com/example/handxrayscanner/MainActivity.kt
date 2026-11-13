package com.example.handxrayscanner

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val MY_PHONEPE_UPI_ID = "7059289878-3@ybl"
    private val PAYEE_NAME = "Hand Scanner App"
    private val AMOUNT = "10.00"

    private lateinit var viewFinder: PreviewView
    private lateinit var xrayOverlay: View
    private lateinit var scanLine: View
    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button
    private lateinit var resultLayout: ScrollView
    private lateinit var tvFreeResults: TextView
    private lateinit var tvPaidResults: TextView
    private lateinit var paymentLockBox: LinearLayout
    private lateinit var btnPayPhonePe: Button
    private lateinit var etTransactionId: EditText
    private lateinit var btnVerifyPayment: Button
    private lateinit var btnScanAgain: Button

    private var handLandmarker: HandLandmarker? = null
    private val cameraExecutor = Executors.newSingleThreadExecutor()
    private var isScanning = false
    private var isHandDetected = false

    private val allStatements = listOf(
        "১. আপনি বাইরে থেকে নিজেকে খুব শান্ত ও হাসিখুশি দেখালেও ভেতরে ভেতরে অনেক কিছু নিয়ে গভীরভাবে চিন্তা করেন।",
        "২. আপনি মানুষকে খুব সহজে বিশ্বাস করেন, আর এই কারণেই মাঝে মাঝে কাছের মানুষের থেকে কষ্ট পান।",
        "৩. আপনার মধ্যে প্রচুর প্রতিভা ও সৃজনশীলতা আছে, কিন্তু সঠিক সুযোগ বা আলস্যের কারণে তা পুরোপুরি কাজে লাগাতে পারছেন না।",
        "৪. অতীতের কিছু ভুল সিদ্ধান্ত নিয়ে আপনি মাঝে মাঝে একা থাকলে অনুশোচনা করেন।",
        "৫. আপনি স্বাধীনচেতা মানুষ; কেউ আপনার ওপর অকারণে খবরদারি করুক তা আপনি একদমই পছন্দ করেন না।",
        "৬. আগামী কয়েক মাসের মধ্যে আপনার আর্থিক অবস্থার উন্নতি হওয়ার প্রবল যোগ রয়েছে।",
        "৭. আপনি পরিবারের মানুষদের খুব ভালোবাসেন, কিন্তু সবসময় মুখ ফুটে তা প্রকাশ করতে পারেন না।",
        "৮. আপনার জীবনে এমন একজন মানুষ আছেন যার কথা আপনি প্রায়ই ভাবেন, কিন্তু তাকে সব কথা বলতে পারেন না।",
        "৯. পরিশ্রম করার মানসিকতা আপনার প্রবল, তবে কাজের শুরুতে একটু দ্বিধাবোধ কাজ করে।",
        "১০. খুব শীঘ্রই এমন একটি সুখবর পেতে চলেছেন যা আপনার দীর্ঘদিনের দুশ্চিন্তা দূর করবে।"
    )

    private var currentShuffledList: List<String> = emptyList()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) startCamera()
        else Toast.makeText(this, "ক্যামেরা পারমিশন প্রয়োজন", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        setupHandLandmarkerAI()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }

        btnScan.setOnClickListener {
            if (isHandDetected && !isScanning) {
                startXRayScan()
            }
        }

        btnPayPhonePe.setOnClickListener { initiatePhonePePayment() }

        btnVerifyPayment.setOnClickListener {
            val utr = etTransactionId.text.toString().trim()
            if (utr.length == 12) unlockAllResults()
            else Toast.makeText(this, "সঠিক ১২ সংখ্যার PhonePe UTR নম্বর দিন", Toast.LENGTH_SHORT).show()
        }

        btnScanAgain.setOnClickListener { resetScanner() }
    }

    private fun initViews() {
        viewFinder = findViewById(R.id.viewFinder)
        xrayOverlay = findViewById(R.id.xrayOverlay)
        scanLine = findViewById(R.id.scanLine)
        tvStatus = findViewById(R.id.tvStatus)
        btnScan = findViewById(R.id.btnScan)
        resultLayout = findViewById(R.id.resultLayout)
        tvFreeResults = findViewById(R.id.tvFreeResults)
        tvPaidResults = findViewById(R.id.tvPaidResults)
        paymentLockBox = findViewById(R.id.paymentLockBox)
        btnPayPhonePe = findViewById(R.id.btnPayPhonePe)
        etTransactionId = findViewById(R.id.etTransactionId)
        btnVerifyPayment = findViewById(R.id.btnVerifyPayment)
        btnScanAgain = findViewById(R.id.btnScanAgain)

        btnPayPhonePe.text = "PhonePe দিয়ে ₹১০ পেমেন্ট করুন
($MY_PHONEPE_UPI_ID)"
    }

    private fun setupHandLandmarkerAI() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.6f)
                .setMinHandPresenceConfidence(0.6f)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setResultListener { result, _ ->
                    val detected = result.landmarks().isNotEmpty()
                    runOnUiThread {
                        if (!isScanning && resultLayout.visibility != View.VISIBLE) {
                            isHandDetected = detected
                            if (detected) {
                                tvStatus.text = "হাত শনাক্ত হয়েছে! স্ক্যান করতে পারেন"
                                tvStatus.setTextColor(Color.parseColor("#00E676"))
                                btnScan.isEnabled = true
                                btnScan.setBackgroundColor(Color.parseColor("#00E5FF"))
                                btnScan.setTextColor(Color.BLACK)
                                btnScan.text = "হাত স্ক্যান করুন (X-Ray)"
                            } else {
                                tvStatus.text = "ক্যামেরার সামনে হাত ধরুন (AI খুঁজছে...)"
                                tvStatus.setTextColor(Color.parseColor("#FF5252"))
                                btnScan.isEnabled = false
                                btnScan.setBackgroundColor(Color.parseColor("#555555"))
                                btnScan.setTextColor(Color.WHITE)
                                btnScan.text = "হাত শনাক্ত হয়নি"
                            }
                        }
                    }
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Toast.makeText(this, "AI মডেল লোড করতে সমস্যা হয়েছে", Toast.LENGTH_LONG).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(viewFinder.surfaceProvider)
            }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also { analysis ->
                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        detectHandInFrame(imageProxy)
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "ক্যামেরা চালু করা যাচ্ছে না", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun detectHandInFrame(imageProxy: ImageProxy) {
        val landmarker = handLandmarker
        if (landmarker == null || isScanning) {
            imageProxy.close()
            return
        }

        val bitmapBuffer = Bitmap.createBitmap(
            imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
        )
        imageProxy.use { bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer) }

        val matrix = Matrix().apply {
            postRotate(imageProxy.imageInfo.rotationDegrees.toFloat())
        }
        val rotatedBitmap = Bitmap.createBitmap(
            bitmapBuffer, 0, 0, bitmapBuffer.width, bitmapBuffer.height, matrix, true
        )

        val mpImage = BitmapImageBuilder(rotatedBitmap).build()
        landmarker.detectAsync(mpImage, SystemClock.uptimeMillis())
    }

    private fun startXRayScan() {
        isScanning = true
        btnScan.isEnabled = false
        tvStatus.text = "ডিজিটাল এক্স-রে স্ক্যান চলছে... হাত স্থির রাখুন"
        tvStatus.setTextColor(Color.CYAN)

        xrayOverlay.visibility = View.VISIBLE
        scanLine.visibility = View.VISIBLE

        val screenHeight = resources.displayMetrics.heightPixels.toFloat()
        val animator = ObjectAnimator.ofFloat(scanLine, "translationY", 0f, screenHeight).apply {
            duration = 1200
            repeatCount = 2
            repeatMode = ValueAnimator.REVERSE
            start()
        }

        Handler(Looper.getMainLooper()).postDelayed({
            animator.cancel()
            xrayOverlay.visibility = View.GONE
            scanLine.visibility = View.GONE
            isScanning = false
            showResults()
        }, 3600)
    }

    private fun showResults() {
        currentShuffledList = allStatements.shuffled()
        tvFreeResults.text = currentShuffledList.take(2).joinToString("

")
        tvPaidResults.text = currentShuffledList.drop(2).joinToString("

")

        paymentLockBox.visibility = View.VISIBLE
        tvPaidResults.visibility = View.GONE
        etTransactionId.visibility = View.GONE
        btnVerifyPayment.visibility = View.GONE
        resultLayout.visibility = View.VISIBLE
    }

    private fun initiatePhonePePayment() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("PhonePe UPI ID", MY_PHONEPE_UPI_ID))

        val uri = Uri.Builder()
            .scheme("upi")
            .authority("pay")
            .appendQueryParameter("pa", MY_PHONEPE_UPI_ID)
            .appendQueryParameter("pn", PAYEE_NAME)
            .appendQueryParameter("tn", "Unlock Hand Report")
            .appendQueryParameter("am", AMOUNT)
            .appendQueryParameter("cu", "INR")
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage("com.phonepe.app")
        }

        try {
            startActivity(intent)
        } catch (e: Exception) {
            try {
                startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW, uri), "পেমেন্ট অ্যাপ বেছে নিন"))
            } catch (ex: Exception) {
                Toast.makeText(this, "UPI ID ($MY_PHONEPE_UPI_ID) কপি হয়েছে। PhonePe থেকে পেমেন্ট করুন।", Toast.LENGTH_LONG).show()
            }
        }
        etTransactionId.visibility = View.VISIBLE
        btnVerifyPayment.visibility = View.VISIBLE
    }

    private fun unlockAllResults() {
        paymentLockBox.visibility = View.GONE
        tvPaidResults.visibility = View.VISIBLE
        Toast.makeText(this, "পেমেন্ট সফল! সম্পূর্ণ রিপোর্ট আনলক হয়েছে।", Toast.LENGTH_LONG).show()
    }

    private fun resetScanner() {
        resultLayout.visibility = View.GONE
        etTransactionId.text.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        handLandmarker?.close()
    }
}
