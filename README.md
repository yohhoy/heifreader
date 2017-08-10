# HeifReader

HEIF(High Efficiency Image Format) reader for Android.

- Support H.265/HEVC(High Efficiency Video Coding) still image only.
- Ignore thumbnail, decode primary image only.
- Ignore ICC profiles, use default colorspace.
- Not support: animation, alpha channel, depthmap, rotation, cropping, tiling, etc.


# Usage

HeifReader provides "decode [`Bitmap`][Bitmap] from HEIF data" static methods.
It is similar to [`BitmapFactory`][BitmapFactory] which are decode `Bitmap` from PNG/JPEG/GIF data.

```java
ImageView imageView = /*...*/;

// you need intialize with application context
HeifReader.initialize(this);

// decode Bitmap from HEIF raw resource
Bitmap bmp = HeifReader.decodeResource(this.getResources(), R.raw.heif_data);
// draw Bitmap on ViewImage
imageView.setImageBitmap(bmp);
```

[Bitmap]: https://developer.android.com/reference/android/graphics/Bitmap.html
[BitmapFactory]: https://developer.android.com/reference/android/graphics/BitmapFactory.html


# Dependencies

HeifReader depends [sannies/mp4parser][mp4parser] library to parse HEIF structure which is based on ISO BMFF, and append some custom Box parsers with `resources/isoparser-custom.properties`.

[mp4parser]: https://github.com/sannies/mp4parser


# License
MIT License
