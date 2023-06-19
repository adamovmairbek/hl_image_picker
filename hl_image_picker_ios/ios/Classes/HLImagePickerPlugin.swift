import Flutter
import Photos
import UIKit
import TLPhotoPicker
import CropViewController
import MobileCoreServices

public class HLImagePickerPlugin: NSObject, FlutterPlugin, TLPhotosPickerViewControllerDelegate, CropViewControllerDelegate, UIImagePickerControllerDelegate & UINavigationControllerDelegate {
    public static func register(with registrar: FlutterPluginRegistrar) {
        let channel = FlutterMethodChannel(name: "hl_image_picker", binaryMessenger: registrar.messenger())
        let instance = HLImagePickerPlugin()
        registrar.addMethodCallDelegate(instance, channel: channel)
    }
    
    var arguments: NSDictionary? = nil
    var uiStyle: [String: Any]? = nil
    var result: FlutterResult? = nil
    
    var configure = TLPhotosPickerConfigure()
    var selectedAssets = [TLPHAsset]()
    let imagePicker = UIImagePickerController()
    
    public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
        switch call.method {
        case "openPicker":
            self.arguments = call.arguments as? NSDictionary
            uiStyle = arguments?["style"] as? [String: Any]
            self.result = result
            self.initConfig()
            self.openPicker()
        case "openCamera":
            self.arguments = call.arguments as? NSDictionary
            uiStyle = arguments?["style"] as? [String: Any]
            self.result = result
            self.openCamera()
        default:
            result(FlutterMethodNotImplemented)
        }
    }
    
    // MARK: Camera
    
    private func openCamera() {
        if UIImagePickerController.isSourceTypeAvailable(UIImagePickerController.SourceType.camera) {
            HLImagePickerUtils.checkCameraPermission { [self] granted in
                if granted {
                    self.imagePicker.delegate = self
                    self.imagePicker.sourceType = .camera
                    self.imagePicker.allowsEditing = false
                    if arguments?["cameraType"] as? String == "video" {
                        self.imagePicker.mediaTypes = [kUTTypeMovie as String]
                        self.imagePicker.videoQuality = .typeHigh
                        let recordVideoMaxSecond = arguments?["recordVideoMaxSecond"] as? Int ?? 60
                        self.imagePicker.videoMaximumDuration = TimeInterval(recordVideoMaxSecond)
                    }
                    DispatchQueue.main.async {
                        UIApplication.topViewController()?.present(self.imagePicker, animated: true, completion: nil)
                    }
                } else {
                    result!(FlutterError(code: "CAMERA_PERMISSION_DENIED", message: "Camera permission denied", details: nil))
                }
            }
        } else {
            result!(FlutterError(code: "CAMERA_NOT_AVAILABLE", message: "Camera is not available", details: nil))
        }
        
    }
    
    public func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
        let isVideo = arguments?["cameraType"] as? String == "video"
        if isVideo {
            if let videoURL = info[.mediaURL] as? URL {
                let asset = AVAsset(url: videoURL)
                let pathStr = videoURL.absoluteString.replacingOccurrences(of: "file://", with: "")
                let videoSize = HLImagePickerUtils.getVideoSize(asset: asset)
                let mimeType = HLImagePickerUtils.getMimeType(url: videoURL)
                var media = [
                    "path": pathStr,
                    "id": pathStr,
                    "name": videoURL.lastPathComponent,
                    "mimeType": mimeType ?? "",
                    "width": Int(videoSize.width) as NSNumber,
                    "height": Int(videoSize.height) as NSNumber,
                    "duration": asset.duration.seconds,
                    "size": HLImagePickerUtils.getFileSize(at: videoURL.path),
                    "type": "video"
                ] as [String : Any]
                let isGenerateThumbnail = arguments?["isExportThumbnail"] as? Bool ?? false
                if isGenerateThumbnail {
                    let compressQuality = arguments?["thumbnailCompressQuality"] as? Double
                    let compressFormat = arguments?["thumbnailCompressFormat"] as? String
                    media["thumbnail"] = HLImagePickerUtils.generateVideoThumbnail(from: videoURL ,quality: compressQuality, format: compressFormat)
                }
                result!(media)
            }
            
            picker.dismiss(animated: true, completion: nil)
        } else {
            let isCropEnabled = arguments?["cropping"] as? Bool ?? false
            if let image = info[.originalImage] as? UIImage {
                if isCropEnabled {
                    openCropper(image: image)
                } else {
                    let imageData = HLImagePickerUtils.copyImage(image)
                    result!(imageData)
                    picker.dismiss(animated: true, completion: nil)
                }
            } else {
                result!(FlutterError(code: "CAMERA_ERROR", message: "Camera error", details: nil))
                picker.dismiss(animated: true, completion: nil)
            }
        }
    }
    
    // MARK: TLPhotoPicker
    
    private func initConfig() {
        switch arguments?["mediaType"] as? String {
        case "video":
            configure.mediaType = .video
            configure.allowedVideoRecording = true
            configure.allowedPhotograph = false
            configure.recordingVideoQuality = .typeHigh
            break
        case "image":
            configure.mediaType = .image
            configure.allowedVideoRecording = false
            break
        default: break
        }
        configure.usedCameraButton = arguments?["usedCameraButton"] as? Bool ?? true
        let recordVideoMaxSecond = arguments?["recordVideoMaxSecond"] as? Int ?? 60
        configure.maxVideoDuration = TimeInterval(recordVideoMaxSecond)
        let numberOfColumn = arguments?["numberOfColumn"] as? Int ?? 3
        configure.numberOfColumn = numberOfColumn
        let maxSelectedAssets = arguments?["maxSelectedAssets"] as? Int ?? 1
        configure.maxSelectedAssets = maxSelectedAssets
        configure.singleSelectedMode = maxSelectedAssets == 1
        configure.previewAtForceTouch = arguments?["enablePreview"] as? Bool ?? false
        configure.cancelTitle = uiStyle?["cancelText"] as? String ?? "Cancel"
        configure.doneTitle = uiStyle?["doneText"] as? String ?? "Done"
        configure.tapHereToChange = uiStyle?["tapHereToChangeText"] as? String ?? "Tap here to change"
        configure.emptyMessage = uiStyle?["emptyMediaText"] as? String ?? "No albums"
        
        var newAssets = [TLPHAsset]()
        if let selecteds = arguments?["selectedIds"] as? NSArray {
            for index in 0..<selecteds.count {
                let assetId = selecteds[index] as! String
                var TLAsset = TLPHAsset.asset(with: assetId)
                TLAsset?.selectedOrder = index + 1
                newAssets.insert(TLAsset!, at: index)
            }
        }
        self.selectedAssets = newAssets
    }
    
    private func openPicker() {
        let picker = TLPhotosPickerViewController()
        picker.delegate = self
        picker.configure = configure
        picker.selectedAssets = self.selectedAssets
        DispatchQueue.main.async {
            UIApplication.topViewController()?.present(picker, animated: true, completion: nil)
        }
    }
    
    public func shouldDismissPhotoPicker(withTLPHAssets: [TLPHAsset]) -> Bool {
        return false
    }
    
    public func dismissPhotoPicker(withTLPHAssets: [TLPHAsset]) {
        if withTLPHAssets.count == 0 {
            result!([] as NSArray);
            UIApplication.topViewController()?.dismiss(animated: true, completion: nil)
            return;
        }
        
        let isSingleMode = configure.singleSelectedMode == true
        let isCropEnabled = arguments?["cropping"] as? Bool ?? false
        let isImagePicker = withTLPHAssets.first?.type == .photo
        if (isImagePicker && isCropEnabled && isSingleMode) {
            guard let asset = withTLPHAssets.first?.fullResolutionImage else { return }
            openCropper(image: asset)
            return;
        }
        
        let loadingAlert = showLoading()
        let group = DispatchGroup()
        var data: Array<NSDictionary> = Array<NSDictionary>()
        let isConvertLivePhoto = arguments?["convertLivePhotosToJPG"] as? Bool ?? true
        let isConvertHeic = arguments?["convertHeicToJPG"] as? Bool ?? false
        for asset in withTLPHAssets {
            group.enter()
            let isHeicPhoto = asset.extType() == .heic
            let isLivePhoto = asset.phAsset?.mediaSubtypes.contains(.photoLive) == true
            if isConvertHeic && isHeicPhoto && !isLivePhoto, let uiImage = asset.fullResolutionImage {
                if let imageInfo = HLImagePickerUtils.copyImage(uiImage) {
                    let media = NSDictionary(dictionary: imageInfo)
                    data.append(media)
                }
                group.leave();
            } else {
                let result = asset.tempCopyMediaFile(convertLivePhotosToJPG: isConvertLivePhoto, completionBlock: { (filePath, fileType) in
                    let media = NSDictionary(dictionary: self.buildResponse(path: filePath, withType: fileType, withAsset: asset))
                    data.append(media)
                    group.leave();
                })
                if result == nil {
                    group.leave();
                }
            }
        }
        group.notify(queue: .main){ [] in
            loadingAlert.dismiss(animated: true, completion: {
                UIApplication.topViewController()?.dismiss(animated: true, completion: nil)
                if data.isEmpty {
                    self.result!(FlutterError(code: "PICKER_ERROR", message: "Picker error", details: nil))
                }else {
                    self.result!(data);
                }
            })
        }
    }
    
    public func canSelectAsset(phAsset: PHAsset) -> Bool {
        let isVideo = phAsset.mediaType == .video
        let isImage = phAsset.mediaType == .image
        if !isVideo && !isImage {
            return false;
        }
        
        let maxDuration = arguments?["maxDuration"] as? Int ?? 0
        if isVideo && maxDuration > 0 {
            if phAsset.duration > TimeInterval(maxDuration) {
                showAlert(message: "maxDurationErrorText")
                return false;
            }
        }
        
        let maxSize = arguments?["maxFileSize"] as? Int64 ?? 0
        if maxSize == 0 {
            return true
        }
        let imageSize = getAssetSize(asset: phAsset)
        if imageSize > maxSize {
            showAlert(message: "maxFileSizeErrorText")
            return false
        }
        return true
    }
    
    public func handleNoAlbumPermissions(picker: TLPhotosPickerViewController) {
        picker.dismiss(animated: true) {
            self.showAlert(message: "noAlbumPermissionText")
        }
    }
    
    public func handleNoCameraPermissions(picker: TLPhotosPickerViewController) {
        showAlert(message: "noCameraPermissionText")
    }
    
    public func didExceedMaximumNumberOfSelection(picker: TLPhotosPickerViewController) {
        showAlert(message: "maxSelectedAssetsErrorText")
    }
    
    private func getAssetSize(asset: PHAsset) -> Int64 {
        let resources = PHAssetResource.assetResources(for: asset)
        guard let resource = resources.first,
              let unsignedInt64 = resource.value(forKey: "fileSize") as? CLong else {
            return 0
        }
        let sizeOnDisk = Int64(bitPattern: UInt64(unsignedInt64))
        return sizeOnDisk
    }
    
    private func buildResponse(path: URL, withType type: String, withAsset asset: TLPHAsset ) -> [String : Any] {
        let phAsset = asset.phAsset
        var media = [
            "path": path.absoluteString.replacingOccurrences(of: "file://", with: ""),
            "id": phAsset?.localIdentifier ?? "",
            "name": asset.originalFileName ?? "",
            "mimeType": type ,
            "width": Int(phAsset?.pixelWidth ?? 0) as NSNumber,
            "height": Int(phAsset?.pixelHeight ?? 0) as NSNumber,
        ] as [String : Any]
        if phAsset?.mediaType == .video {
            media["type"] = "video"
            asset.videoSize { mediaSize in
                media["size"] = mediaSize
            }
            let isGenerateThumbnail = arguments?["isExportThumbnail"] as? Bool ?? false
            if isGenerateThumbnail {
                let compressQuality = arguments?["thumbnailCompressQuality"] as? Double
                let compressFormat = arguments?["thumbnailCompressFormat"] as? String
                media["thumbnail"] = HLImagePickerUtils.generateVideoThumbnail(from: path ,quality: compressQuality, format: compressFormat)
            }
            media["duration"] = phAsset?.duration ?? 0
        } else {
            media["type"] = "image"
            asset.photoSize { mediaSize in
                media["size"] = mediaSize
            }
        }
        return media
    }
    
    private func showAlert(message: String) {
        let alert = UIAlertController(title: "", message: uiStyle?[message] as? String ?? message, preferredStyle: .alert)
        alert.addAction(UIAlertAction(title: uiStyle?["okText"] as? String ?? "OK", style: .default, handler: nil))
        UIApplication.topViewController()?.present(alert, animated: true, completion: nil)
    }
    
    private func showLoading() -> UIAlertController {
        let alertController = UIAlertController(title: nil, message: uiStyle?["loadingText"] as? String ?? "Loading...", preferredStyle: .alert)
        var indicatorStyle: UIActivityIndicatorView.Style
        if #available(iOS 13.0, *) {
            indicatorStyle  = .large
        } else {
            indicatorStyle = .gray
        }
        let activityIndicator = UIActivityIndicatorView(style: indicatorStyle)
        activityIndicator.translatesAutoresizingMaskIntoConstraints = false
        activityIndicator.startAnimating()
        alertController.view.addSubview(activityIndicator)
        NSLayoutConstraint.activate([
            activityIndicator.leadingAnchor.constraint(equalTo: alertController.view.leadingAnchor, constant: 20),
            activityIndicator.centerYAnchor.constraint(equalTo: alertController.view.centerYAnchor)
        ])
        UIApplication.topViewController()?.present(alertController, animated: true, completion: nil)
        return alertController
    }
    
    // MARK: CropViewController
    private func openCropper(image: UIImage) {
        let cropViewController = CropViewController(image: image)
        cropViewController.delegate = self
        let aspectRatioX = arguments?["ratioX"] as? Double
        let aspectRatioY = arguments?["ratioY"] as? Double
        if aspectRatioX != nil && aspectRatioY != nil {
            cropViewController.customAspectRatio = CGSize(width: aspectRatioX!, height: aspectRatioY!)
            cropViewController.resetAspectRatioEnabled = false
            cropViewController.aspectRatioPickerButtonHidden = true
            cropViewController.aspectRatioLockDimensionSwapEnabled = true
            cropViewController.aspectRatioLockEnabled = true
        }
        if let aspectRatioPresets = arguments?["aspectRatioPresets"] as? [NSString] {
            var allowedAspectRatios = [CropViewControllerAspectRatioPreset]()
            for preset in aspectRatioPresets {
                let aspectRatio = parseAspectRatio(name: preset)
                allowedAspectRatios.append(aspectRatio)
            }
            cropViewController.allowedAspectRatios = allowedAspectRatios
        }
        DispatchQueue.main.async {
            UIApplication.topViewController()?.present(cropViewController, animated: true, completion: nil)
        }
    }
    
    public func cropViewController(_ cropViewController: CropViewController, didCropToImage image: UIImage, withRect cropRect: CGRect, angle: Int) {
        let compressQuality = arguments?["compressQuality"] as? Double
        let compressFormat = arguments?["compressFormat"] as? String
        let croppedImage = HLImagePickerUtils.copyImage(image, quality: compressQuality, format: compressFormat)
        DispatchQueue.main.async {
            UIApplication.topViewController()?.dismiss(animated: false, completion: {
                UIApplication.topViewController()?.dismiss(animated: true, completion: {
                    if croppedImage != nil {
                        if (self.arguments?["cameraType"] as? String) != nil {
                            self.result!(croppedImage)
                        } else {
                            self.result!([croppedImage])
                        }
                    } else {
                        self.result!(FlutterError(code: "CROP_ERROR", message: "Crop error", details: nil))
                    }
                })
            })
        }
    }
    
    public func cropViewController(_ cropViewController: CropViewController, didFinishCancelled cancelled: Bool) {
        if cancelled {
            DispatchQueue.main.async {
                UIApplication.topViewController()?.dismiss(animated: false, completion: nil)
            }
        }
    }
    
    private func parseAspectRatio(name: NSString) -> CropViewControllerAspectRatioPreset {
        if name == "square" {
            return .presetSquare
        } else if name == "3x2" {
            return .preset3x2
        } else if name == "4x3" {
            return .preset4x3
        } else if name == "5x3" {
            return .preset5x3
        } else if name == "5x4" {
            return .preset5x4
        } else if name == "7x5" {
            return .preset7x5
        } else if name == "16x9" {
            return .preset16x9
        } else {
            return .presetOriginal
        }
    }
}

extension UIApplication {
    class func topViewController(base: UIViewController? = UIApplication.shared.keyWindow?.rootViewController) -> UIViewController? {
        if let nav = base as? UINavigationController {
            return topViewController(base: nav.visibleViewController)
        }
        if let tab = base as? UITabBarController {
            if let selected = tab.selectedViewController {
                return topViewController(base: selected)
            }
        }
        if let presented = base?.presentedViewController {
            return topViewController(base: presented)
        }
        return base
    }
}