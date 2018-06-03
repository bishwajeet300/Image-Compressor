package com.bishwajeet.imagecompressor;

import android.app.ProgressDialog;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import org.opencv.android.Utils;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by bishwajeetkumar on 06/02/17.
 */

public class Compressor {
    private static final String LOG_TAG = Compressor.class.getSimpleName();

    static volatile Compressor singleton = null;
    private static Context mContext;

    public Compressor(Context context) {
        mContext = context;
    }

    // initialise the class and set the context
    public static Compressor with(Context context) {
        if (singleton == null) {
            synchronized (Compressor.class) {
                if (singleton == null) {
                    singleton = new Builder(context).build();
                }
            }
        }
        return singleton;

    }

    /**
     * Compresses the image at the specified Uri String and and return the filepath of the compressed image.
     *
     * @param imageUri imageUri Uri (String) of the source image you wish to compress
     * @return filepath
     */
    public String compress(String imageUri) {
        return compressImage(imageUri);
    }

    /**
     * Compresses the image at the specified Uri String and and return the filepath of the compressed image.
     *
     * @param imageUri imageUri Uri (String) of the source image you wish to compress
     * @return filepath
     */
    public String compress(String imageUri, boolean deleteSourceImage) {

        String compressUri = compressImage(imageUri);

        if (deleteSourceImage) {
            File source = new File(getRealPathFromURI(imageUri));
            if (source.exists()) {
                boolean isdeleted = source.delete();
                Log.d(LOG_TAG, (isdeleted) ? "SourceImage File deleted" : "SourceImage File not deleted");
            }
        }

        return compressUri;
    }


    public String compress(int drawableID) throws IOException {

        // Create a bitmap from this drawable
        Bitmap bitmap = BitmapFactory.decodeResource(mContext.getApplicationContext().getResources(), drawableID);
        if (null != bitmap) {
            // Create a file from the bitmap

            // Create an image file name
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String imageFileName = "JPEG_" + timeStamp + "_";
            File storageDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_PICTURES);
            File image = File.createTempFile(
                    imageFileName,  /* prefix */
                    ".jpg",         /* suffix */
                    storageDir      /* directory */
            );
            FileOutputStream out = new FileOutputStream(image);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);

            // Compress the new file
            Uri copyImageUri = Uri.fromFile(image);

            String compressImagePath = compressImage(copyImageUri.toString());

            // Delete the file create from the drawable Id
            if (image.exists()) {
                boolean isdeleted = image.delete();
                Log.d(LOG_TAG, (isdeleted) ? "SourceImage File deleted" : "SourceImage File not deleted");
            }

            // return the path to the compress image
            return compressImagePath;
        }

        return null;
    }


    /**
     * Compresses the image at the specified Uri String and and return the bitmap data of the compressed image.
     *
     * @param imageUri imageUri Uri (String) of the source image you wish to compress
     * @return Bitmap format of the new image file (compressed)
     * @throws IOException
     */
    public Bitmap getCompressBitmap(String imageUri) throws IOException {
        File imageFile = new File(compressImage(imageUri));
        Uri newImageUri = Uri.fromFile(imageFile);
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), newImageUri);
        return bitmap;
    }

    /**
     * Compresses the image at the specified Uri String and and return the bitmap data of the compressed image.
     *
     * @param imageUri          Uri (String) of the source image you wish to compress
     * @param deleteSourceImage If True will delete the source file
     * @return Compress image bitmap
     * @throws IOException
     */
    public Bitmap getCompressBitmap(String imageUri, boolean deleteSourceImage) throws IOException {
        File imageFile = new File(compressImage(imageUri));
        Uri newImageUri = Uri.fromFile(imageFile);
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(mContext.getContentResolver(), newImageUri);

        if (deleteSourceImage) {
            File source = new File(getRealPathFromURI(imageUri));
            if (source.exists()) {
                boolean isdeleted = source.delete();
                Log.d(LOG_TAG, (isdeleted) ? "SourceImage File deleted" : "SourceImage File not deleted");
            }
        }
        return bitmap;
    }

    // Actually does the compression of the Image
    private String compressImage(String imageUri) {

        String filePath = getRealPathFromURI(imageUri);
        Bitmap scaledBitmap = null;

        BitmapFactory.Options options = new BitmapFactory.Options();

//      by setting this field as true, the actual bitmap pixels are not loaded in the memory. Just the bounds are loaded. If
//      you try the use the bitmap here, you will get null.
        options.inJustDecodeBounds = true;
        Bitmap bmp = BitmapFactory.decodeFile(filePath, options);

        int actualHeight = options.outHeight;
        int actualWidth = options.outWidth;
        float maxHeight, maxWidth;

        if (actualHeight > actualWidth) {
            maxHeight = 1795.0f;
            maxWidth = 1287.0f;
        } else if (actualHeight < actualWidth) {
            maxWidth = 1795.0f;
            maxHeight = 1287.0f;
        } else {
            maxHeight = 1795.0f;
            maxWidth = 1795.0f;
        }

        float imgRatio = (actualHeight * 1.0f) / (actualWidth * 1.0f);
        float heightRatio = actualHeight / maxHeight;
        float widthRatio = actualWidth / maxWidth;

        if (heightRatio > widthRatio) {
            actualWidth = (int) maxWidth;
            actualHeight = (int) (maxWidth * imgRatio);
        } else if (heightRatio < widthRatio) {
            actualHeight = (int) maxHeight;
            actualWidth = (int) (maxHeight / imgRatio);
        } else {
            actualHeight = (int) maxHeight;
            actualWidth = (int) maxWidth;
        }

//      setting inSampleSize value allows to load a scaled down version of the original image

        options.inSampleSize = calculateInSampleSize(options, actualWidth, actualHeight);

//      inJustDecodeBounds set to false to load the actual bitmap
        options.inJustDecodeBounds = false;

//      this options allow android to claim the bitmap memory if it runs low on memory
        options.inPurgeable = true;
        options.inInputShareable = true;
        options.inTempStorage = new byte[16 * 1024];

        try {
//          load the bitmap from its path
            bmp = BitmapFactory.decodeFile(filePath, options);
        } catch (OutOfMemoryError exception) {
            exception.printStackTrace();

        }
        try {
            scaledBitmap = Bitmap.createBitmap(actualWidth, actualHeight, Bitmap.Config.ARGB_8888);
        } catch (OutOfMemoryError exception) {
            exception.printStackTrace();
        }

        float ratioX = actualWidth / (float) options.outWidth;
        float ratioY = actualHeight / (float) options.outHeight;
        float middleX = actualWidth / 2.0f;
        float middleY = actualHeight / 2.0f;

        Matrix scaleMatrix = new Matrix();
        scaleMatrix.setScale(ratioX, ratioY, middleX, middleY);

        Canvas canvas = new Canvas(scaledBitmap);
        canvas.setMatrix(scaleMatrix);
        canvas.drawBitmap(bmp, middleX - bmp.getWidth() / 2, middleY - bmp.getHeight() / 2, new Paint(Paint.FILTER_BITMAP_FLAG));

//      check the rotation of the image and display it properly
        ExifInterface exif;
        try {
            exif = new ExifInterface(filePath);

            int orientation = exif.getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, 0);
            Log.d("EXIF", "Exif: " + orientation);
            Matrix matrix = new Matrix();
            if (orientation == 6) {
                matrix.postRotate(90);
                Log.d("EXIF", "Exif: " + orientation);
            } else if (orientation == 3) {
                matrix.postRotate(180);
                Log.d("EXIF", "Exif: " + orientation);
            } else if (orientation == 8) {
                matrix.postRotate(270);
                Log.d("EXIF", "Exif: " + orientation);
            }
            scaledBitmap = Bitmap.createBitmap(scaledBitmap, 0, 0,
                    scaledBitmap.getWidth(), scaledBitmap.getHeight(), matrix,
                    true);
        } catch (IOException e) {
            e.printStackTrace();
        }

        FileOutputStream out = null;
        String filename = getFilename();
        try {
            out = new FileOutputStream(filename);

//          write the compressed bitmap at the destination specified by filename.
            scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return filename;

    }

    private int calculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {
        final int height = options.outHeight;
        final int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqHeight || width > reqWidth) {
            final int heightRatio = Math.round((float) height / (float) reqHeight);
            final int widthRatio = Math.round((float) width / (float) reqWidth);
            inSampleSize = heightRatio < widthRatio ? heightRatio : widthRatio;
        }
        final float totalPixels = width * height;
        final float totalReqPixelsCap = reqWidth * reqHeight * 2;
        while (totalPixels / (inSampleSize * inSampleSize) > totalReqPixelsCap) {
            inSampleSize++;
        }

        return inSampleSize;
    }

    private String getFilename() {
        File file = new File(Environment.getExternalStorageDirectory().getPath(), "SiliCompressor/Images");
        if (!file.exists()) {
            file.mkdirs();
        }
        String uriSting = (file.getAbsolutePath() + "/" + System.currentTimeMillis() + ".jpg");
        return uriSting;

    }

    /**
     * Gets a valid path from the supply contentURI
     *
     * @param contentURI
     * @return A validPath of the image
     */
    private String getRealPathFromURI(String contentURI) {
        Uri contentUri = Uri.parse(contentURI);
        Cursor cursor = mContext.getContentResolver().query(contentUri, null, null, null, null);
        if (cursor == null) {
            return contentUri.getPath();
        } else {
            cursor.moveToFirst();
            int index = cursor.getColumnIndex(MediaStore.Images.ImageColumns.DATA);
            String str = cursor.getString(index);
            cursor.close();
            return str;
        }
    }

    public String imageQuality(String imageUri) {
        String filePath = getRealPathFromURI(imageUri);

        //Get bitmap for original Image
        BitmapFactory.Options opt = new BitmapFactory.Options();
        opt.inDither = true;
        opt.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap originalImageBitmap = BitmapFactory.decodeFile(filePath, opt);

        //Get initial matrix of original image bitmap
        Mat originalMatImage = new Mat();
        Utils.bitmapToMat(originalImageBitmap, originalMatImage);

        //Get grayscale version of original MstImage
        Mat originalMatImageGrayscale = new Mat();
        Imgproc.cvtColor(originalMatImage, originalMatImageGrayscale, Imgproc.COLOR_BGR2GRAY);

        //Create destination Image bitmap from original Image bitmap
        Bitmap destinationImageBitmap = Bitmap.createBitmap(originalImageBitmap);

        //Get destination Image matrix from destination Image bitmap
        Mat destinationImageMat = new Mat();
        Utils.bitmapToMat(destinationImageBitmap, destinationImageMat);

        //Get Laplacian image mat from destination Image matrix
        Mat laplacianImageMat = new Mat();
        int l = CvType.CV_8UC1; //8-bit grey scale image
        destinationImageMat.convertTo(laplacianImageMat, l);

        //calculates the Laplacian of the source image by adding up the second x and y derivatives calculated using the Sobel operator
        Imgproc.Laplacian(originalMatImageGrayscale, laplacianImageMat, CvType.CV_8U);

        //Convert Laplacian 8-bit image mat to Laplacian grayscale image mat
        Mat laplacianImage8bitMat = new Mat();
        laplacianImageMat.convertTo(laplacianImage8bitMat, l);


        //Create bitmap of the Laplacian 8-bit image dimensions
        Bitmap bmp = Bitmap.createBitmap(laplacianImage8bitMat.cols(), laplacianImage8bitMat.rows(), Bitmap.Config.ARGB_8888);

        //Convert Mat to Image of the same
        Utils.matToBitmap(laplacianImage8bitMat, bmp);
        int[] pixels = new int[bmp.getHeight() * bmp.getWidth()];
        bmp.getPixels(pixels, 0, bmp.getWidth(), 0, 0, bmp.getWidth(), bmp.getHeight());

        int maxLap = -16777216;

        for (int i = 0; i < pixels.length; i++) {
            if (pixels[i] > maxLap) {
                Log.e("maxLap", "Pixel @ " + i + ": " + String.valueOf(pixels[i]) + ", MaxLap: " + maxLap);
                Log.e("maxLap", "x = " + i % bmp.getWidth() + ", y = " + i / bmp.getWidth());
                //bmp.setPixel(i % bmp.getWidth(), i / bmp.getWidth() , Color.MAGENTA);
                maxLap = pixels[i];
            }
        }

        int soglia = -6118750;
        Log.e("maxLap", "MaxLap: " + maxLap);
        if (maxLap < soglia || maxLap == soglia) {
            return "blur image";
        } else {
            return "Not a blur image";
        }
    }

    /**
     * Fluent API for creating {@link Compressor} instances.
     */
    public static class Builder {

        private final Context context;


        /**
         * Start building a new {@link Compressor} instance.
         */
        public Builder(Context context) {
            if (context == null) {
                throw new IllegalArgumentException("Context must not be null.");
            }
            this.context = context.getApplicationContext();
        }


        /**
         * Create the {@link Compressor} instance.
         */
        public Compressor build() {
            Context context = this.context;

            return new Compressor(context);
        }
    }

    class ImageCompressionAsyncTask extends AsyncTask<String, Void, String> {
        ProgressDialog mProgressDialog;

        public ImageCompressionAsyncTask(Context context) {

        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

        }

        @Override
        protected String doInBackground(String... params) {

            String filePath = compressImage(params[0]);
            return filePath;
        }

        @Override
        protected void onPostExecute(String s) {


        }
    }
}
