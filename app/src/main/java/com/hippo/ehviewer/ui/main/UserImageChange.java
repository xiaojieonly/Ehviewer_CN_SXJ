package com.hippo.ehviewer.ui.main;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.transition.Slide;
import android.transition.TransitionSet;
import android.transition.Visibility;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.hippo.content.FileProvider;
import com.hippo.ehviewer.R;
import com.hippo.ehviewer.Settings;
import com.hippo.ehviewer.callBack.ImageChangeCallBack;
import com.hippo.ehviewer.callBack.PermissionCallBack;
import com.hippo.util.FileUtils;
import com.hippo.util.PermissionRequester;
import com.hippo.widget.AvatarImageView;

import java.io.File;
import java.io.IOException;

public class UserImageChange implements PermissionCallBack {

    public static final int CHANGE_BACKGROUND = 0;
    public static final int CHANGE_AVATAR = 1;
    public static final int TAKE_CAMERA = 101;
    public static final int PICK_PHOTO = 102;
    public static final int REQUEST_CAMERA_PERMISSION = 1;
    public static final int REQUEST_STORAGE_PERMISSION = 2;

    @NonNull
    private final Activity activity;
    @NonNull
    private final LayoutInflater layoutInflater;
    @NonNull
    private final LayoutInflater rootLayoutInflater;
    private final int dialogType;
    @NonNull
    private final String key;

    @Nullable
    private PopupWindow popupWindow;
    private final AlertDialog alertDialog;

    private Uri imageUri;
    private File outputImage;

    private ImageChangeCallBack imageChangeCallBack;


    public UserImageChange(@NonNull Activity activity,
                           int dialogType,
                           @NonNull LayoutInflater layoutInflater,
                           @NonNull LayoutInflater rootLayoutInflater,
                           ImageChangeCallBack imageChangeCallBack) {
        this.activity = activity;
        this.rootLayoutInflater = rootLayoutInflater;
        this.layoutInflater = layoutInflater;
        this.dialogType = dialogType;
        this.imageChangeCallBack = imageChangeCallBack;

        if (dialogType == CHANGE_AVATAR) {
            alertDialog = new AlertDialog.Builder(this.activity)
                    .setMessage(R.string.change_avatar_message)
                    .setTitle(R.string.change_avatar_title)
                    .setPositiveButton(R.string.yes, (dialog, which) -> yes())
                    .setNegativeButton(R.string.cancel, this::cancel)
                    .create();
            key = Settings.USER_AVATAR_IMAGE;
        } else {
            alertDialog = new AlertDialog.Builder(this.activity)
                    .setMessage(R.string.change_background_message)
                    .setTitle(R.string.change_background_title)
                    .setPositiveButton(R.string.yes, (dialog, which) -> yes())
                    .setNegativeButton(R.string.cancel, this::cancel)
                    .create();
            key = Settings.USER_BACKGROUND_IMAGE;
        }

    }

    public void showImageChangeDialog() {
        if (Settings.getUserImageFile(key) != null) {
            yes();
        }else {
            alertDialog.show();
        }

    }

    @SuppressLint("InflateParams")
    private void yes() {
        RelativeLayout relativeLayout = (RelativeLayout) layoutInflater.inflate(R.layout.background_change_bottom_pop, null);
        TextView startCamera = relativeLayout.findViewById(R.id.take_photo_with_camera);

        startCamera.setOnClickListener(l -> startCamera());

        TextView startAlbum = relativeLayout.findViewById(R.id.choose_from_the_album);
        startAlbum.setOnClickListener(l -> startAlbum());
        popupWindow = new PopupWindow(relativeLayout, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        popupWindow.setOutsideTouchable(true);
        popupWindow.setTouchable(true);

        TransitionSet enterTransitionSet = new TransitionSet();
        enterTransitionSet.setDuration(300);
        Slide enterSlide = new Slide(Gravity.BOTTOM);
        enterSlide.setMode(Visibility.MODE_IN);
        enterTransitionSet.addTransition(enterSlide);
        enterTransitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
        popupWindow.setEnterTransition(enterTransitionSet);

        TransitionSet exitTransitionSet = new TransitionSet();
        exitTransitionSet.setDuration(300);
        Slide exitSlide = new Slide(Gravity.BOTTOM);
        exitSlide.setMode(Visibility.MODE_OUT);
        exitTransitionSet.addTransition(exitSlide);
        exitTransitionSet.setOrdering(TransitionSet.ORDERING_TOGETHER);
        popupWindow.setExitTransition(exitTransitionSet);


        popupWindow.showAtLocation(rootLayoutInflater.inflate(R.layout.activity_main, null), Gravity.BOTTOM, 0, 0);

    }

    private void cancel(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_NEGATIVE) {
            dialog.dismiss();
        }
    }

    private void startCamera() {
        assert popupWindow != null;
        popupWindow.dismiss();
        if (dialogType == CHANGE_BACKGROUND) {
            outputImage = new File(activity.getExternalCacheDir(), "background_image.jpg");
        } else {
            outputImage = new File(activity.getExternalCacheDir(), "avatar_image.jpg");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            //大于等于版本24（7.0）的场合
            String authority = activity.getApplication().getPackageName() + ".fileprovider";
            imageUri = FileProvider.getUriForFile(activity, authority, outputImage);
        } else {
            //小于android 版本7.0（24）的场合
            imageUri = Uri.fromFile(outputImage);
        }

        PermissionRequester.request(activity, Manifest.permission.CAMERA,
                activity.getString(R.string.request_camera_permission),
                REQUEST_CAMERA_PERMISSION, this);


    }

    private void startAlbum() {
        assert popupWindow != null;
        popupWindow.dismiss();
        PermissionRequester.request(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE,
                activity.getString(R.string.request_storage_permission),
                REQUEST_STORAGE_PERMISSION, this);
    }

    public void saveImageForResult(int requestCode, int resultCode, @Nullable Intent data, AvatarImageView avatar, ImageView background) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }
        if (requestCode == PICK_PHOTO) {
            assert data != null;
            saveImageFromAlbum(data, avatar, background);
        } else {
            saveImageFromCamera(avatar, background);
        }

    }

    private void saveImageFromCamera(AvatarImageView avatar, ImageView background) {
        Settings.saveFilePath(key,
                outputImage.getPath());
        if (dialogType == CHANGE_BACKGROUND) {
            imageChangeCallBack.backgroundSourceChange(new File(outputImage.getPath()));
//            background.setImageBitmap(BitmapFactory.decodeFile(outputImage.getPath()));
        } else {
            avatar.setImageBitmap(BitmapFactory.decodeFile(outputImage.getPath()));
        }
    }

    private void saveImageFromAlbum(Intent data, AvatarImageView avatar, ImageView background) {

        String imagePath = null;
        Uri uri = data.getData();
        if (DocumentsContract.isDocumentUri(activity, uri)) {
            // 如果是document类型的Uri，则通过document id处理
            String docId = DocumentsContract.getDocumentId(uri);
            assert uri != null;
            if ("com.android.providers.media.documents".equals(uri.getAuthority())) {
                String id = docId.split(":")[1];
                // 解析出数字格式的id
                String selection = MediaStore.Images.Media._ID + "=" + id;
                imagePath = getImagePath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, selection);
            } else if ("com.android.providers.downloads.documents".equals(uri.getAuthority())) {
                Uri contentUri = ContentUris.withAppendedId(Uri.parse("content: //downloads/public_downloads"), Long.parseLong(docId));
                imagePath = getImagePath(contentUri, null);
            }
        } else {
            assert uri != null;
            if ("content".equalsIgnoreCase(uri.getScheme())) {
                // 如果是content类型的Uri，则使用普通方式处理
                imagePath = getImagePath(uri, null);
            } else if ("file".equalsIgnoreCase(uri.getScheme())) {
                // 如果是file类型的Uri，直接获取图片路径即可
                imagePath = uri.getPath();
            }
        }
        // 根据图片路径显示图片
        saveImage(imagePath, avatar, background);
    }

    private void saveImage(String imagePath, AvatarImageView avatar, ImageView background) {

        if (imagePath == null){
            return;
        }

        File oleFile;

        oleFile = Settings.getUserImageFile(key);
        if (oleFile != null) {
            oleFile.delete();
        }

        File newFile = new File(imagePath);
        File toFile = new File(activity.getExternalCacheDir(), newFile.getName());
        if (!toFile.exists()) {
            try {
                toFile.createNewFile();
            } catch (IOException ioException) {
                ioException.printStackTrace();
            }
        }
        FileUtils.copyFile(newFile, toFile);
        String newImagePath = toFile.getPath();
        Settings.saveFilePath(key, newImagePath);
        if (dialogType == CHANGE_BACKGROUND) {
            imageChangeCallBack.backgroundSourceChange(new File(newImagePath));
//            background.setImageBitmap(BitmapFactory.decodeFile(toFile.getPath()));
        } else {
            avatar.setImageBitmap(BitmapFactory.decodeFile(toFile.getPath()));
        }
    }

    private String getImagePath(Uri uri, String selection) {
        String path = null;
        // 通过Uri和selection来获取真实的图片路径
        Cursor cursor = activity.getContentResolver().query(uri, null, selection, null, null);
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(MediaStore.Images.Media.DATA);
                if (columnIndex == -1){
                    return null;
                }
                path = cursor.getString(columnIndex);
            }
            cursor.close();
        }
        return path;
    }

    /**
     * 获取权限的回调
     *
     * @param permissionCode
     */
    @Override
    public void agree(int permissionCode) {
        if (permissionCode == REQUEST_CAMERA_PERMISSION) {
            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
            activity.startActivityForResult(intent, TAKE_CAMERA);
        }
        if (permissionCode == REQUEST_STORAGE_PERMISSION) {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            activity.startActivityForResult(intent, PICK_PHOTO);
        }

    }
}
