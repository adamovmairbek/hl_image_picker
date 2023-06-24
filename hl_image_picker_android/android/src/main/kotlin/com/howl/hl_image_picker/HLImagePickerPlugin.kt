package com.howl.hl_image_picker

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.TextUtils
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.InjectResourceSource
import com.luck.picture.lib.config.PictureMimeType
import com.luck.picture.lib.config.SelectLimitType
import com.luck.picture.lib.config.SelectMimeType
import com.luck.picture.lib.config.SelectModeConfig
import com.luck.picture.lib.config.SelectorConfig
import com.luck.picture.lib.dialog.RemindDialog
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnInjectLayoutResourceListener
import com.luck.picture.lib.interfaces.OnResultCallbackListener
import com.luck.picture.lib.permissions.PermissionConfig
import com.luck.picture.lib.style.BottomNavBarStyle
import com.luck.picture.lib.style.PictureSelectorStyle
import com.luck.picture.lib.style.SelectMainStyle
import com.luck.picture.lib.style.TitleBarStyle
import com.luck.picture.lib.utils.DateUtils
import com.luck.picture.lib.utils.MediaUtils
import com.yalantis.ucrop.UCrop
import com.yalantis.ucrop.UCropImageEngine
import com.yalantis.ucrop.model.AspectRatio
import com.yalantis.ucrop.util.FileUtils
import com.yalantis.ucrop.view.CropImageView
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry
import java.io.File


/** HLImagePickerPlugin */
class HLImagePickerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware, PluginRegistry.ActivityResultListener {
    private lateinit var channel: MethodChannel
    private var currentActivity: Activity? = null
    private lateinit var applicationContext: Context

    companion object {
        const val CROPPER_RESULT_CODE = 301
    }

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "hl_image_picker")
        channel.setMethodCallHandler(this)
        applicationContext = flutterPluginBinding.applicationContext
    }

    private var flutterCall: MethodCall? = null
    private var mediaPickerResult: Result? = null
    private lateinit var uiStyle: Map<String, Any>

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "openPicker" -> {
                this.flutterCall = call
                this.uiStyle = call.argument<Map<String, Any>>("localized") ?: mapOf()
                this.mediaPickerResult = result
                openPicker()
            }

            "openCamera" -> {
                this.flutterCall = call
                this.uiStyle = call.argument<Map<String, Any>>("localized") ?: mapOf()
                this.mediaPickerResult = result
                openCamera()
            }

            "openCropper" -> {
                this.flutterCall = call
                this.uiStyle = call.argument<Map<String, Any>>("localized") ?: mapOf()
                this.mediaPickerResult = result
                openCropper()
            }

            else -> {
                result.notImplemented()
            }
        }
    }

    private fun openCamera() {
        val cameraType = when (flutterCall?.argument<String>("cameraType")) {
            "video" -> SelectMimeType.ofVideo()
            else -> SelectMimeType.ofImage()
        }
        val recordVideoMaxSecond = flutterCall?.argument<Int>("recordVideoMaxSecond") ?: 60
        PictureSelector.create(currentActivity)
                .openCamera(cameraType)
                .setCropEngine(getCropFileEngine())
                .setVideoThumbnailListener(getVideoThumbnail())
                .setRecordVideoMaxSecond(recordVideoMaxSecond)
                .setPermissionDeniedListener { fragment, permissionArray, _, _ ->
                    handlePermissionDenied(fragment, permissionArray)
                }
                .forResultActivity(object : OnResultCallbackListener<LocalMedia> {
                    override fun onResult(result: ArrayList<LocalMedia>?) {
                        if (result != null) {
                            mediaPickerResult?.success(buildResponse(result[0]))
                        } else {
                            mediaPickerResult?.error("CAMERA_ERROR", "Camera error", null)
                        }
                    }

                    override fun onCancel() {

                    }
                })
    }

    private fun openPicker() {
        val mediaType = when (flutterCall?.argument<String>("mediaType")) {
            "video" -> SelectMimeType.ofVideo()
            "image" -> SelectMimeType.ofImage()
            else -> SelectMimeType.ofAll()
        }
        val selectedAssets: MutableList<LocalMedia> = mutableListOf()
        val selectedIds = flutterCall?.argument<ArrayList<String>>("selectedIds") ?: ArrayList()
        for (i in 0 until selectedIds.size) {
            val localMedia: LocalMedia = LocalMedia.generateLocalMedia(applicationContext, selectedIds[i])
            selectedAssets.add(localMedia)
        }
        val numberOfColumn = flutterCall?.argument<Int>("numberOfColumn") ?: 3
        val enablePreview = flutterCall?.argument<Boolean>("enablePreview") ?: false
        val maxSelectedAssets = flutterCall?.argument<Int>("maxSelectedAssets") ?: 1
        val minSelectedAssets = flutterCall?.argument<Int>("minSelectedAssets") ?: 0
        val selectionMode = if (maxSelectedAssets == 1) SelectModeConfig.SINGLE else SelectModeConfig.MULTIPLE
        val usedCameraButton = flutterCall?.argument<Boolean>("usedCameraButton") ?: true
        val maxFileSize = flutterCall?.argument<Int>("maxFileSize") ?: 0
        val minFileSize = flutterCall?.argument<Int>("minFileSize") ?: 0
        val recordVideoMaxSecond = flutterCall?.argument<Int>("recordVideoMaxSecond") ?: 60
        val maxDuration = flutterCall?.argument<Int>("maxDuration") ?: 0
        val minDuration = flutterCall?.argument<Int>("minDuration") ?: 0

        PictureSelector.create(currentActivity)
                .openGallery(mediaType)
                .setMaxSelectNum(maxSelectedAssets)
                .setMinSelectNum(minSelectedAssets)
                .setSelectionMode(selectionMode)
                .setImageSpanCount(numberOfColumn)
                .isPreviewImage(enablePreview)
                .isPreviewVideo(enablePreview)
                .isPreviewAudio(enablePreview)
                .setSelectedData(selectedAssets)
                .isWithSelectVideoImage(true)
                .setMaxVideoSelectNum(maxSelectedAssets)
                .setSelectMaxFileSize(maxFileSize.toLong())
                .setSelectMinFileSize(minFileSize.toLong())
                .isDisplayCamera(usedCameraButton)
                .setImageEngine(GlideEngine.createGlideEngine())
                .setCropEngine(getCropFileEngine())
                .setVideoThumbnailListener(getVideoThumbnail())
                .setRecordVideoMaxSecond(recordVideoMaxSecond)
                .setSelectMaxDurationSecond(maxDuration)
                .setSelectMinDurationSecond(minDuration)
                .setPermissionDeniedListener { fragment, permissionArray, _, _ ->
                    handlePermissionDenied(fragment, permissionArray)
                }
                .setCustomLoadingListener { context -> CustomLoadingDialog(context, uiStyle.getOrElse("loadingText") { "Loading" } as String) }
                .setSelectLimitTipsListener { context, _, config, limitType ->
                    handleSelectLimitTips(context, config, limitType)
                }
                .setDefaultAlbumName(uiStyle.getOrElse("defaultAlbumName") { "Recents" } as String)
                .setSelectorUIStyle(handleUIStyle())
                .isEmptyResultReturn(true)
                .setInjectLayoutResourceListener(InjectLayoutResourceListener())
                .forResult(object : OnResultCallbackListener<LocalMedia?> {
                    override fun onResult(result: ArrayList<LocalMedia?>?) {
                        val mediaList: MutableList<Map<String, Any>> = mutableListOf()
                        result?.forEach { media ->
                            if (media != null) {
                                mediaList.add(buildResponse(media))
                            }
                        }
                        mediaPickerResult?.success(mediaList)
                    }

                    override fun onCancel() {}
                })
    }

    private fun handleUIStyle(): PictureSelectorStyle {
        val style = PictureSelectorStyle()
        val mainStyle = SelectMainStyle()
        mainStyle.isDarkStatusBarBlack = true
        mainStyle.mainListBackgroundColor = Color.parseColor("#FFFFFF")
        mainStyle.previewBackgroundColor = Color.parseColor("#FFFFFF")
        mainStyle.isPreviewDisplaySelectGallery = true
        mainStyle.isPreviewSelectRelativeBottom = true
        mainStyle.isCompleteSelectRelativeTop = true
        mainStyle.selectText = uiStyle.getOrElse("doneText") {"Done"} as String
        mainStyle.selectNormalText = uiStyle.getOrElse("doneText") {"Done"} as String
        mainStyle.selectTextColor = Color.parseColor("#007AFF")
        mainStyle.selectNormalTextColor = Color.parseColor("#007AFF")
        val maxSelectedAssets = flutterCall?.argument<Int>("maxSelectedAssets") ?: 1
        if (maxSelectedAssets != 1) {
            mainStyle.isSelectNumberStyle = true
            mainStyle.selectBackground = R.drawable.hl_multiple_selector
        } else {
            mainStyle.selectBackground = R.drawable.hl_single_selector
        }
        mainStyle.previewSelectBackground = R.drawable.hl_preview_selector
        mainStyle.adapterCameraText = " "
        mainStyle.adapterCameraTextSize = 1

        val titleBarStyle = TitleBarStyle()
        titleBarStyle.isHideCancelButton = true
        titleBarStyle.titleBackgroundColor = Color.parseColor("#FFFFFF")
        titleBarStyle.titleTextColor = Color.parseColor("#000000")
        titleBarStyle.titleLeftBackResource = R.drawable.ps_ic_black_back
        titleBarStyle.titleDrawableRightResource = R.drawable.hl_arrow_down
        titleBarStyle.isDisplayTitleBarLine = true

        val bottomBarStyle = BottomNavBarStyle()
        bottomBarStyle.bottomNarBarBackgroundColor = Color.parseColor("#FFFFFF")
        bottomBarStyle.isCompleteCountTips = false
        bottomBarStyle.bottomPreviewSelectTextColor = Color.parseColor("#007AFF")

        style.selectMainStyle = mainStyle
        style.titleBarStyle = titleBarStyle
        style.bottomBarStyle = bottomBarStyle
        return style
    }

    private class InjectLayoutResourceListener : OnInjectLayoutResourceListener {
        override fun getLayoutResourceId(context: Context, resourceSource: Int): Int {
            return when (resourceSource) {
                InjectResourceSource.MAIN_SELECTOR_LAYOUT_RESOURCE -> R.layout.hl_custom_fragment_selector
                else -> 0
            }
        }
    }

    private fun handleSelectLimitTips(context: Context?, config: SelectorConfig, limitType: Int): Boolean {
        return when (limitType) {
            SelectLimitType.SELECT_MAX_SELECT_LIMIT -> {
                showDialog(context, uiStyle.getOrElse("maxSelectedAssetsErrorText") { "Exceeded maximum number of selected items" } as String)
                true
            }

            SelectLimitType.SELECT_MIN_SELECT_LIMIT -> {
                showDialog(context, uiStyle.getOrElse("minSelectedAssetsErrorText") { "Need to select at least ${config.minSelectNum}" } as String)
                true
            }

            SelectLimitType.SELECT_MAX_VIDEO_SELECT_LIMIT -> {
                showDialog(context, uiStyle.getOrElse("maxSelectedAssetsErrorText") { "Exceeded maximum number of selected items" } as String)
                true
            }

            SelectLimitType.SELECT_MIN_VIDEO_SELECT_LIMIT -> {
                showDialog(context, uiStyle.getOrElse("minSelectedAssetsErrorText") { "Need to select at least ${config.minSelectNum}" } as String)
                true
            }

            SelectLimitType.SELECT_MAX_VIDEO_SECOND_SELECT_LIMIT -> {
                showDialog(context, uiStyle.getOrElse("maxDurationErrorText") { "Exceeded maximum duration of the video" } as String)
                true
            }

            SelectLimitType.SELECT_MIN_VIDEO_SECOND_SELECT_LIMIT -> {
                showDialog(context, uiStyle.getOrElse("minDurationErrorText") { "The video is too short" } as String)
                true
            }

            SelectLimitType.SELECT_MAX_FILE_SIZE_LIMIT -> {
                showDialog(context, uiStyle.getOrElse("maxFileSizeErrorText") {"Exceeded maximum file size"} as String)
                true
            }

            SelectLimitType.SELECT_MIN_FILE_SIZE_LIMIT -> {
                showDialog(context, uiStyle.getOrElse("minFileSizeErrorText") {"The file size is too small"} as String)
                true
            }

            else -> false
        }
    }

    private fun handlePermissionDenied(fragment: Fragment, permissionArray: Array<String>?) {
        val message: String = if (TextUtils.equals(permissionArray?.get(0), PermissionConfig.CAMERA[0])) {
            uiStyle.getOrElse("noCameraPermissionText") {"No permission to access camera"} as String
        } else if (TextUtils.equals(permissionArray?.get(0), Manifest.permission.RECORD_AUDIO)) {
            uiStyle.getOrElse("noRecordAudioPermissionText") {"No permission to record audio"} as String
        } else {
            uiStyle.getOrElse("noAlbumPermissionText") {"No permission to access album"} as String
        }
        showDialog(fragment.context, message)
    }

    private fun showDialog(context: Context?, message: String) {
        val dialog = RemindDialog.buildDialog(context, message)
        dialog.setButtonText(uiStyle.getOrElse("okText") {"OK"} as String)
        dialog.setButtonTextColor(Color.parseColor("#007AFF"))
        dialog.setContentTextColor(Color.parseColor("#000000"))
        dialog.setOnDialogClickListener {
            dialog.dismiss()
        }
        dialog.show()
    }

    private fun buildResponse(media: LocalMedia): Map<String, Any> {
        if (media.width == 0 || media.height == 0) {
            if (PictureMimeType.isHasImage(media.mimeType)) {
                val imageExtraInfo = MediaUtils.getImageSize(applicationContext, media.path)
                media.width = imageExtraInfo.width
                media.height = imageExtraInfo.height
            } else if (PictureMimeType.isHasVideo(media.mimeType)) {
                val imageExtraInfo = MediaUtils.getVideoSize(applicationContext, media.path)
                media.width = imageExtraInfo.width
                media.height = imageExtraInfo.height
            }
        }

        val item = mutableMapOf<String, Any>()
        item["id"] = media.path
        item["name"] = media.fileName
        item["mimeType"] = media.mimeType
        item["size"] = media.size
        item["type"] = "image"

        if (PictureMimeType.isHasVideo(media.mimeType)) {
            item["type"] = "video"
            item["duration"] = media.duration.toDouble()
            if (media.videoThumbnailPath != null) {
                item["thumbnail"] = media.videoThumbnailPath
            }
        }

        if (media.isCut) {
            item["width"] = media.cropImageWidth
            item["height"] = media.cropImageHeight
            item["path"] = media.cutPath
        } else {
            item["width"] = media.width
            item["height"] = media.height
            item["path"] = media.realPath
        }
        return item
    }


    private fun openCropper() {
        val imagePath = flutterCall?.argument<String>("imagePath") ?: return
        val inputUri = if (PictureMimeType.isContent(imagePath)) Uri.parse(imagePath) else Uri.fromFile(File(imagePath))
        val sandboxPath = getSandboxPath()
        if (sandboxPath == null) {
            mediaPickerResult?.error("CROPPER_ERROR", "Can't get output path", null)
            return
        }
        val compressQuality = flutterCall?.argument<Double>("compressQuality") ?: 0.9
        val compressFormatStr = flutterCall?.argument<String>("compressFormat")
        val compressFormat = if ("png" == compressFormatStr) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        val fileExt = if ("png" == compressFormatStr) ".png" else ".jpg"
        val destinationUri = Uri.fromFile(
                File(getSandboxPath(), DateUtils.getCreateFileName("hl_image_picker_") + fileExt))
        val uCrop = UCrop.of<Any>(inputUri, destinationUri)
        val options = UCrop.Options()
        options.setCompressionFormat(compressFormat)
        options.setCompressionQuality((compressQuality * 100).toInt())
        val aspectRatioX = flutterCall?.argument<Double>("ratioX")
        val aspectRatioY = flutterCall?.argument<Double>("ratioY")
        if (aspectRatioX != null && aspectRatioY != null) {
            options.withAspectRatio(aspectRatioX.toFloat(), aspectRatioY.toFloat())
        }
        val aspectRatioPresets = flutterCall?.argument<ArrayList<String>>("aspectRatioPresets")
        if (aspectRatioPresets != null) {
            val aspectRatioList = ArrayList<AspectRatio>()
            for (i in aspectRatioPresets.indices) {
                val preset = aspectRatioPresets[i]
                val aspectRatio = parseAspectRatio(preset)
                aspectRatioList.add(aspectRatio)
            }
            options.setAspectRatioOptions(0, *aspectRatioList.toTypedArray())
        }

        val croppingStyle = flutterCall?.argument<String>("croppingStyle") ?: "normal"
        if (croppingStyle == "circular") {
            options.setCircleDimmedLayer(true)
            options.setShowCropGrid(false)
            options.setShowCropFrame(false)
            options.withAspectRatio(1F, 1F)
        }
        uCrop.withOptions(options)
        val maxWidth = flutterCall?.argument<Int>("cropMaxWidth")
        val maxHeight = flutterCall?.argument<Int>("cropMaxHeight")
        if(maxWidth != null && maxHeight != null) {
            uCrop.withMaxResultSize(maxWidth, maxHeight)
        }
        uCrop.setImageEngine(object : UCropImageEngine {
            override fun loadImage(context: Context, url: String, imageView: ImageView) {
                if (!ImageLoaderUtils.assertValidRequest(context)) {
                    return
                }
                Glide.with(context).load(url).override(180, 180).into(imageView)
            }

            override fun loadImage(context: Context, url: Uri, maxWidth: Int, maxHeight: Int, call: UCropImageEngine.OnCallbackListener<Bitmap>) {
                Glide.with(context).asBitmap().load(url).override(maxWidth, maxHeight).into(object : CustomTarget<Bitmap?>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap?>?) {
                        call.onCall(resource)
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        call.onCall(null)
                    }
                })
            }
        })
        currentActivity?.let { uCrop.start(it, CROPPER_RESULT_CODE) }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == CROPPER_RESULT_CODE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                val outputUri = UCrop.getOutput(data)
                val imagePath = outputUri?.path
                if(outputUri == null || imagePath == null) {
                    mediaPickerResult?.error("CROPPER_ERROR", "Crop error", null)
                    return true
                }
                val imageFile = File(imagePath)
                val item = mutableMapOf<String, Any>()
                item["id"] = imagePath
                item["path"] = imagePath
                item["name"] = imageFile.name
                item["mimeType"] = getPathToMimeType(imagePath)
                item["size"] = imageFile.length()
                item["type"] = "image"
                item["width"] = UCrop.getOutputImageWidth(data)
                item["height"] = UCrop.getOutputImageHeight(data)
                mediaPickerResult?.success(item)
            } else {
                mediaPickerResult?.error("CROPPER_ERROR", "Crop error", null)
            }
            return true
        }
        return false
    }

    private fun getPathToMimeType(path: String): String {
        val mimeType: String = if (FileUtils.isContent(path)) {
            FileUtils.getMimeTypeFromMediaContentUri(applicationContext, Uri.parse(path))
        } else {
            FileUtils.getMimeTypeFromMediaContentUri(applicationContext, Uri.fromFile(File(path)))
        }
        return mimeType
    }

    private fun getSandboxPath(): String? {
        val externalFilesDir: File = applicationContext.getExternalFilesDir("") ?: return null
        val customFile = File(externalFilesDir.absolutePath, "HLPicker")
        if (!customFile.exists()) {
            customFile.mkdirs()
        }
        return customFile.absolutePath + File.separator
    }

    private fun getCropFileEngine(): ImageFileCropEngine? {
        val isCropEnabled = flutterCall?.argument<Boolean>("cropping") ?: false
        val maxSelectedAssets = flutterCall?.argument<Int>("maxSelectedAssets") ?: 1
        if (!isCropEnabled || maxSelectedAssets != 1) {
            return null
        }
        val options = UCrop.Options()
        val aspectRatioX = flutterCall?.argument<Double>("ratioX")
        val aspectRatioY = flutterCall?.argument<Double>("ratioY")
        if (aspectRatioX != null && aspectRatioY != null) {
            options.withAspectRatio(aspectRatioX.toFloat(), aspectRatioY.toFloat())
        }
        val aspectRatioPresets = flutterCall?.argument<ArrayList<String>>("aspectRatioPresets")
        if (aspectRatioPresets != null) {
            val aspectRatioList = ArrayList<AspectRatio>()
            for (i in aspectRatioPresets.indices) {
                val preset = aspectRatioPresets[i]
                val aspectRatio = parseAspectRatio(preset)
                aspectRatioList.add(aspectRatio)
            }
            options.setAspectRatioOptions(0, *aspectRatioList.toTypedArray())
        }
        val compressQuality = flutterCall?.argument<Double>("compressQuality") ?: 0.9
        options.setCompressionQuality((compressQuality * 100).toInt())
        val compressFormat = flutterCall?.argument<String>("compressFormat")
        options.setCompressionFormat(if ("png" == compressFormat) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG)
        val outputPath = getSandboxPath()
        if (outputPath != null) {
            options.setCropOutputPathDir(outputPath)
        }
        val fileExt = if ("png" == compressFormat) ".png" else ".jpg"
        options.setCropOutputFileName(DateUtils.getCreateFileName("hl_image_picker_") + fileExt)
        
        val croppingStyle = flutterCall?.argument<String>("croppingStyle") ?: "normal"
        if (croppingStyle == "circular") {
            options.setCircleDimmedLayer(true)
            options.setShowCropGrid(false)
            options.setShowCropFrame(false)
            options.withAspectRatio(1F, 1F)
        }
        val maxWidth = flutterCall?.argument<Int>("cropMaxWidth")
        val maxHeight = flutterCall?.argument<Int>("cropMaxHeight")
        return ImageFileCropEngine(options, maxWidth, maxHeight)
    }

    private fun getVideoThumbnail(): VideoThumbnailEventListener? {
        val isExportThumbnail = flutterCall?.argument<Boolean>("isExportThumbnail") ?: false
        if (!isExportThumbnail) {
            return null
        }
        val externalFilesDir = applicationContext.getExternalFilesDir("")
        val customFile = File(externalFilesDir?.absolutePath, "Thumbnail")
        if (!customFile.exists()) {
            customFile.mkdirs()
        }
        val compressQuality = flutterCall?.argument<Double>("thumbnailCompressQuality") ?: 0.9
        val compressFormatStr = flutterCall?.argument<String>("thumbnailCompressFormat")
        val compressFormat = if ("png" == compressFormatStr) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
        return VideoThumbnailEventListener(customFile.absolutePath + File.separator, (compressQuality * 100).toInt(), compressFormat)
    }

    private fun parseAspectRatio(name: String): AspectRatio {
        return when (name) {
            "square" -> {
                AspectRatio(null, 1.0f, 1.0f)
            }

            "3x2" -> {
                AspectRatio(null, 3.0f, 2.0f)
            }

            "4x3" -> {
                AspectRatio(null, 4.0f, 3.0f)
            }

            "5x3" -> {
                AspectRatio(null, 5.0f, 3.0f)
            }

            "5x4" -> {
                AspectRatio(null, 5.0f, 4.0f)
            }

            "7x5" -> {
                AspectRatio(null, 7.0f, 5.0f)
            }

            "16x9" -> {
                AspectRatio(null, 16.0f, 9.0f)
            }

            else -> {
                val aspectTitle = "Original"
                AspectRatio(aspectTitle, CropImageView.SOURCE_IMAGE_ASPECT_RATIO, 1.0f)
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        currentActivity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        currentActivity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        currentActivity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        currentActivity = null
    }
}
