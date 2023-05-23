package com.example.kidsdrawingapp

import android.Manifest
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.text.isDigitsOnly
import androidx.core.view.get
import androidx.lifecycle.lifecycleScope
import com.example.kidsdrawingapp.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var mImageButtonCurrentPaint: ImageButton? = null
    var customProgressDialog: Dialog? = null

    //compileSdkVersion 必須31以上
    private val requestPermission: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            permissions.entries.forEach {
                val permissionName = it.key
                val isGranted = it.value

                if (isGranted) {
                    Toast.makeText(this, "權限已許可", Toast.LENGTH_LONG).show()

                    val pickIntent = Intent(Intent.ACTION_PICK,MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                    openGalleryLauncher.launch(pickIntent)
                } else {
                    if (permissionName == Manifest.permission.READ_EXTERNAL_STORAGE) {
                        Toast.makeText(this, "你拒絕了此權限", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

    private val openGalleryLauncher : ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
            result ->
            if (result.resultCode == RESULT_OK && result.data!=null){
                binding.ivBackground.setImageURI(result.data?.data)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        mImageButtonCurrentPaint = binding.llPaintColors[2] as ImageButton
        mImageButtonCurrentPaint!!.setImageDrawable(
            ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
        )

        binding.drawingView.setSizeForBrush(20.toFloat())
        binding.ibBrush.setOnClickListener { showBrushSizeChooserDialog() }

        binding.ibGallery.setOnClickListener {requestStoragePermission() }
        binding.ibUndo.setOnClickListener { binding.drawingView.onClickUndo() }
        binding.ibRedo.setOnClickListener { binding.drawingView.onClickRedo() }
        binding.ibSave.setOnClickListener {
            if (isReadStorageAllowed()){
                showProgressDialog()
                lifecycleScope.launch {
                    saveBitmapFile(getBitmapFromView(binding.flDrawingViewContainer))
                }
            }
        }
    }

    private fun showBrushSizeChooserDialog() {
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)
        brushDialog.setTitle("Brush size:")
        val smallBtn: ImageButton = brushDialog.findViewById(R.id.ib_small_brush)
        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.ib_medium_brush)
        val largeBtn: ImageButton = brushDialog.findViewById(R.id.ib_large_brush)
        smallBtn.setOnClickListener {
            binding.drawingView.setSizeForBrush(10.toFloat())
            brushDialog.dismiss()
        }
        mediumBtn.setOnClickListener {
            binding.drawingView.setSizeForBrush(20.toFloat())
            brushDialog.dismiss()
        }
        largeBtn.setOnClickListener {
            binding.drawingView.setSizeForBrush(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
    }

    fun paintClicked(view: View) {
        if (view != mImageButtonCurrentPaint) {
            val imageButton = view as ImageButton
            val colorTag = imageButton.tag.toString()
            binding.drawingView.setColor(colorTag)

            imageButton.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_pressed)
            )

            mImageButtonCurrentPaint?.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.pallet_normal)
            )

            mImageButtonCurrentPaint = view
        }
    }

    private fun showRationaleDialog(
        title: String,
        message: String,
    ) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun isReadStorageAllowed(): Boolean{
        val result = ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)
        return result == PackageManager.PERMISSION_GRANTED
    }
    private fun requestStoragePermission(){
        if (ActivityCompat.shouldShowRequestPermissionRationale(
                this,Manifest.permission.READ_EXTERNAL_STORAGE)
        ){
            showRationaleDialog("Kids Drawing App","該App需要您的外部存取權限")
        }else{
            requestPermission.launch(arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ))
        }
    }
    /**
     * 這個函數的目的是從給定的View中創建一個Bitmap。
     * @param view 是想要轉換為Bitmap的視圖對象。
     * @return 返回創建的Bitmap。
     */
    private fun getBitmapFromView(view:View) : Bitmap{
        // 創建一個Bitmap對象，該對象的寬度和高度與給定的視圖相同，並使用ARGB_8888配置來存儲圖像數據。
        // ARGB_8888是一種高質量的位圖配置，它將每個顏色像素存儲為4個字節。
        val returnedBitmap = Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)

        // 創建一個新的Canvas並給它一個Bitmap來繪製。
        // 以後在此Canvas上的所有繪製操作都將在該Bitmap上顯示。
        val canvas = Canvas(returnedBitmap)

        // 從給定的視圖中獲取背景Drawable。
        // 如果該視圖有背景，則將其繪製到我們的canvas（和Bitmap）上。
        // 如果沒有，則將canvas背景塗為白色。
        val bgDrawable = view.background
        if (bgDrawable != null){
            bgDrawable.draw(canvas)
        }else{
            canvas.drawColor(Color.WHITE)
        }

        // 在給定的canvas上繪製視圖。
        // 這將包含視圖的所有內容，如文字、圖片等。
        view.draw(canvas)

        // 返回我們創建的Bitmap。
        return returnedBitmap
    }

    private suspend fun saveBitmapFile(mBitmap: Bitmap?) {
        if (mBitmap != null) {
            try {
                val filename = "KidDrawingApp_" + System.currentTimeMillis() / 1000 + ".png"

                val resolver = contentResolver
                val contentValues = ContentValues()
                contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                contentValues.put(MediaStore.MediaColumns.RELATIVE_PATH, "DCIM/" + filename)


                val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                if (imageUri != null) {
                    val outputStream = resolver.openOutputStream(imageUri)
                    if (outputStream != null) {
                        mBitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
                        outputStream.close()
                        withContext(Dispatchers.Main) {
                            cancelProgressDialog()
                            Toast.makeText(this@MainActivity, "檔案儲存成功: $imageUri", Toast.LENGTH_SHORT).show()
                            shareImage(imageUri)
                        }
                    }
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    cancelProgressDialog()
                    Toast.makeText(this@MainActivity, "檔案儲存失敗: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

//    private suspend fun saveBitmapFile(mBitmap: Bitmap?): String {
//        var result = ""
//        withContext(Dispatchers.IO) {
//            if (mBitmap != null) {
//                try {
//                    val bytes = ByteArrayOutputStream()
//                    mBitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)
//
//                    val f = File(
//                        externalCacheDir?.absoluteFile.toString()
//                                + File.separator + "KidDrawingApp_" + System.currentTimeMillis() / 1000 + ".png"
//                    )
//
//                    val fo = FileOutputStream(f)
//                    fo.write(bytes.toByteArray())
//                    fo.close()
//
//                    result = f.absolutePath
//
//                    runOnUiThread {
//                        cancelProgressDialog()
//                        if (result != ""){
//                            Toast.makeText(this@MainActivity,"檔案儲存成功: $result",Toast.LENGTH_SHORT).show()
//                            shareImage(result)
//                        }else{
//                            Toast.makeText(this@MainActivity,"檔案儲存失敗: $result",Toast.LENGTH_SHORT).show()
//
//                        }
//                    }
//                }catch (e:Exception){
//                    result = ""
//                    e.printStackTrace()
//                }
//            }
//        }
//        return result
//    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if (customProgressDialog!=null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(result: Uri){
        // 啟動媒體掃描器掃描指定的文件
        MediaScannerConnection.scanFile(this, arrayOf(result.toString()),null){
            path, uri ->
            // 創建一個新的 Intent 用於分享
            val shareIntent = Intent()
            // 設置 Intent 的動作為 ACTION_SEND，表示這是一個分享動作
            shareIntent.action = Intent.ACTION_SEND
            // 添加文件的 URI 到 Intent 的額外數據中
            shareIntent.putExtra(Intent.EXTRA_STREAM,uri)
            // 設置分享內容的類型為 PNG 格式的圖像
            shareIntent.type = "image/png"
            // 創建一個 Intent 選擇器，並啟動該 Intent 選擇器
            startActivity(Intent.createChooser(shareIntent,"Share"))
        }
    }
}