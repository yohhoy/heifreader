/*
 * Copyright (c) 2017 yohhoy
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package jp.yohhoy.heifreader;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.SystemClock;
import android.support.v8.renderscript.Allocation;
import android.support.v8.renderscript.Element;
import android.support.v8.renderscript.RenderScript;
import android.support.v8.renderscript.Type;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import org.mp4parser.Box;
import org.mp4parser.IsoFile;
import org.mp4parser.boxes.iso14496.part12.FileTypeBox;
import org.mp4parser.boxes.iso14496.part15.HevcConfigurationBox;
import org.mp4parser.boxes.iso14496.part15.HevcDecoderConfigurationRecord;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.util.ArrayList;
import java.util.List;

import jp.yohhoy.heifreader.iso14496.part12.ItemLocationBox;    // PATCHED
import jp.yohhoy.heifreader.iso14496.part12.ItemPropertiesBox;
import jp.yohhoy.heifreader.iso14496.part12.ItemPropertyAssociation;
import jp.yohhoy.heifreader.iso14496.part12.ItemPropertyContainerBox;
import jp.yohhoy.heifreader.iso14496.part12.PrimaryItemBox;
import jp.yohhoy.heifreader.iso23008.part12.ImageSpatialExtentsBox;


/**
 * HEIF(High Efficiency Image Format) reader
 *
 * Create Bitmap object from HEIF file, byte-array, stream, etc.
 */
public class HeifReader {
    private static final String TAG = "HeifReader";

    /**
     * input data size limitation for safety.
     */
    private static final long LIMIT_FILESIZE = 20 * 1024 * 1024;  // 20[MB]

    private static RenderScript mRenderScript;
    private static File mCacheDir;

    /**
     * Initialize HeifReader module.
     *
     * @param context Context.
     */
    public static void initialize(Context context) {
        mRenderScript = RenderScript.create(context);
        mCacheDir = context.getCacheDir();
    }

    /**
     * Decode a bitmap from the specified byte array.
     *
     * @param data byte array of compressed image data.
     * @return The decoded bitmap, or null if the image could not be decoded.
     */
    public static Bitmap decodeByteArray(byte[] data) {
        assertPrecondition();
        try {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            IsoFile isoFile = new IsoFile(Channels.newChannel(bais));
            ImageInfo info = parseHeif(isoFile);

            ByteBuffer bitstream = extractBitstream(data, info);
            ImageReader reader = ImageReader.newInstance(info.size.getWidth(), info.size.getHeight(), ImageFormat.YV12, 1);
            try {
                renderHevcImage(bitstream, info, reader.getSurface());
                return convertToBitmap(reader.acquireNextImage());
            } finally {
                reader.close();
            }
        } catch (IOException ex) {
            Log.e(TAG, "decodeByteArray failure", ex);
            return null;
        }
    }

    /**
     * Decode a file path into a bitmap.
     *
     * @param pathName complete path name for the file to be decoded.
     * @return The decoded bitmap, or null if the image could not be decoded.
     */
    public static Bitmap decodeFile(String pathName) {
        assertPrecondition();
        try {
            File file = new File(pathName);
            long fileSize = file.length();
            if (LIMIT_FILESIZE < fileSize) {
                Log.e(TAG, "file size exceeds limit(" + LIMIT_FILESIZE + ")");
                return null;
            }
            byte[] data = new byte[(int) fileSize];
            FileInputStream fis = new FileInputStream(file);
            try {
                fis.read(data);
                return decodeByteArray(data);
            } finally {
                fis.close();
            }
        } catch (IOException ex) {
            Log.e(TAG, "decodeFile failure", ex);
            return null;
        }
    }

    /**
     * Decode a raw resource into a bitmap.
     *
     * @param res The resources object containing the image data.
     * @param id The resource id of the image data.
     * @return The decoded bitmap, or null if the image could not be decoded.
     */
    public static Bitmap decodeResource(Resources res, int id) {
        assertPrecondition();
        try {
            int length = (int) res.openRawResourceFd(id).getLength();
            byte data[] = new byte[length];
            res.openRawResource(id).read(data);
            return decodeByteArray(data);
        } catch (IOException ex) {
            Log.e(TAG, "decodeResource failure", ex);
            return null;
        }
    }

    /**
     * Decode an input stream into a bitmap.
     *
     * This method save input stream to temporary file on cache directory, because HEIF data
     * structure requires multi-pass parsing.
     *
     * @param is The input stream that holds the raw data to be decoded into a bitmap.
     * @return The decoded bitmap, or null if the image could not be decoded.
     */
    public static Bitmap decodeStream(InputStream is) {
        assertPrecondition();
        try {
            // write stream to temporary file
            File heifFile = File.createTempFile("heifreader", "heif", mCacheDir);
            FileOutputStream fos = new FileOutputStream(heifFile);
            try {
                byte[] buf = new byte[4096];
                int totalLength = 0;
                int len;
                while ((len = is.read(buf)) > 0) {
                    fos.write(buf, 0, len);
                    totalLength += len;
                    if (LIMIT_FILESIZE < totalLength) {
                        Log.e(TAG, "data size exceeds limit(" + LIMIT_FILESIZE + ")");
                        return null;
                    }
                }
            } finally {
                fos.close();
            }
            return decodeFile(heifFile.getAbsolutePath());
        } catch (IOException ex) {
            Log.e(TAG, "decodeStream failure", ex);
            return null;
        }
    }

    private static void assertPrecondition() {
        if (mRenderScript == null) {
            throw new IllegalStateException("HeifReader is not initialized.");
        }
    }

    private static ImageInfo parseHeif(IsoFile isoFile) throws IOException {
        // validate brand compatibility ('ftyp' box)
        List<FileTypeBox> ftypBoxes = isoFile.getBoxes(FileTypeBox.class);
        if (ftypBoxes.size() != 1) {
            throw new IOException("FileTypeBox('ftyp') shall be unique");
        }
        FileTypeBox ftypBox = ftypBoxes.get(0);
        Log.d(TAG, "HEIC ftyp=" + ftypBox);
        if (!"mif1".equals(ftypBox.getMajorBrand()) || ftypBox.getCompatibleBrands().indexOf("heic") < 0) {
            throw new IOException("unsupported FileTypeBox('ftyp') brands");
        }

        // get primary item_ID
        List<PrimaryItemBox> pitmBoxes = isoFile.getBoxes(PrimaryItemBox.class, true);
        if (pitmBoxes.isEmpty()) {
            throw new IOException("PrimaryItemBox('pitm') not found");
        }
        PrimaryItemBox pitmBox = pitmBoxes.get(0);
        pitmBox.parseDetails();
        Log.d(TAG, "HEIC primary item_ID=" + pitmBox.getItemId());

        // get associative item properties
        ItemPropertiesBox iprpBox = isoFile.getBoxes(ItemPropertiesBox.class, true).get(0);
        ItemPropertyAssociation ipmaBox = iprpBox.getBoxes(ItemPropertyAssociation.class).get(0);
        ItemPropertyContainerBox ipcoBox = iprpBox.getBoxes(ItemPropertyContainerBox.class).get(0);
        List<Box> primaryPropBoxes = new ArrayList<>();
        for (ItemPropertyAssociation.Item item : ipmaBox.getItems()) {
            if (item.item_ID == pitmBox.getItemId()) {
                for (ItemPropertyAssociation.Assoc assoc : item.associations) {
                    primaryPropBoxes.add(ipcoBox.getBoxes().get(assoc.property_index - 1));
                }
            }
        }

        // get image size
        ImageInfo info = new ImageInfo();
        ImageSpatialExtentsBox ispeBox = findBox(primaryPropBoxes, ImageSpatialExtentsBox.class);
        if (ispeBox == null) {
            throw new IOException("ImageSpatialExtentsBox('ispe') not found");
        }
        info.size = new Size((int) ispeBox.display_width, (int) ispeBox.display_height);
        Log.i(TAG, "HEIC image size=" + ispeBox.display_width + "x" + ispeBox.display_height);

        // get HEVC decoder configuration
        HevcConfigurationBox hvccBox = findBox(primaryPropBoxes, HevcConfigurationBox.class);
        if (hvccBox == null) {
            throw new IOException("HevcConfigurationBox('hvcC') not found");
        }
        HevcDecoderConfigurationRecord hevcConfig = hvccBox.getHevcDecoderConfigurationRecord();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final byte[] startCode = {0x00, 0x00, 0x00, 0x01};
        for (HevcDecoderConfigurationRecord.Array params : hevcConfig.getArrays()) {
            for (byte[] nalUnit : params.nalUnits) {
                baos.write(startCode);
                baos.write(nalUnit);
            }
        }
        info.paramset = ByteBuffer.wrap(baos.toByteArray());
        Log.d(TAG, "HEIC HEVC profile=" + hevcConfig.getGeneral_profile_idc()
                + " level=" + (hevcConfig.getGeneral_level_idc() / 30f)
                + " bitDepth=" + (hevcConfig.getBitDepthLumaMinus8() + 8));
        if (hevcConfig.getLengthSizeMinusOne() + 1 != 4) {
            throw new IOException("unsupported DecoderConfigurationRecord.LengthSizeMinusOne("
                    + hevcConfig.getLengthSizeMinusOne() + ")");
        }

        // get bitstream position
        List<ItemLocationBox> ilocBoxes = isoFile.getBoxes(ItemLocationBox.class, true);
        if (ilocBoxes.isEmpty()) {
            throw new IOException("ItemLocationBox('iloc') not found");
        }
        ItemLocationBox ilocBox = ilocBoxes.get(0);
        ilocBox.parseDetails();
        for (ItemLocationBox.Item item : ilocBox.getItems()) {
            if (item.itemId == pitmBox.getItemId()) {
                info.offset = (int) item.baseOffset;
                info.length = (int) item.extents.get(0).extentLength;
                break;
            }
        }
        Log.d(TAG, "HEIC bitstream offset=" + info.offset + " length=" + info.length);
        return info;
    }

    private static ByteBuffer extractBitstream(byte[] heif, ImageInfo info) {
        // extract HEVC bitstream
        ByteBuffer bitstream = ByteBuffer.allocate(info.length)
                .put(heif, info.offset, info.length)
                .order(ByteOrder.BIG_ENDIAN);
        bitstream.rewind();
        // convert hvcC format to Annex.B format
        do {
            int pos = bitstream.position();
            int size = bitstream.getInt();  // hevcConfig.getLengthSizeMinusOne()==3
            bitstream.position(pos);
            bitstream.putInt(1);    // start_code={0x00 0x00 0x00 0x01}
            bitstream.position(bitstream.position() + size);
        } while (bitstream.remaining() > 0);
        bitstream.rewind();
        return bitstream;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Box> T findBox(List<Box> container, Class<T> clazz) {
        for (Box box : container) {
            if (clazz.isInstance(box)) {
                return (T) box;
            }
        }
        return null;
    }

    private static MediaCodec findHevcDecoder() {
        // "video/hevc" may select hardware decoder on the device.
        // "OMX.google.hevc.decoder" is software decoder.
        final String[] codecNames = {"video/hevc", "OMX.google.hevc.decoder"};

        for (String name : codecNames) {
            try {
                MediaCodec codec = MediaCodec.createByCodecName(name);
                Log.i(TAG, "codec \"" + name + "\" is available");
                return codec;
            } catch (IOException | IllegalArgumentException ex) {
                Log.d(TAG, "codec \"" + name + "\" not found");
            }
        }
        return null;
    }

    private static void renderHevcImage(ByteBuffer bitstream, ImageInfo info, Surface surface) {
        MediaCodec decoder = findHevcDecoder();
        if (decoder == null) {
            throw new RuntimeException("no HEVC decoding support");
        }

        // configure HEVC decoder
        MediaFormat inputFormat = MediaFormat.createVideoFormat("video/hevc", info.size.getWidth(), info.size.getHeight());
        inputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, bitstream.limit());
        inputFormat.setByteBuffer("csd-0", info.paramset);
        Log.d(TAG, "HEVC input-format=" + inputFormat);
        decoder.configure(inputFormat, surface, null, 0);
        MediaFormat outputFormat = decoder.getOutputFormat();
        Log.d(TAG, "HEVC output-format=" + outputFormat);

        long beginTime = SystemClock.elapsedRealtimeNanos();
        decoder.start();
        try {
            // set bitstream to decoder
            int inputBufferId = decoder.dequeueInputBuffer(-1);
            if (inputBufferId < 0) {
                throw new IllegalStateException("dequeueInputBuffer return " + inputBufferId);
            }
            ByteBuffer inBuffer = decoder.getInputBuffer(inputBufferId);
            inBuffer.put(bitstream);
            decoder.queueInputBuffer(inputBufferId, 0, bitstream.limit(), 0, 0);

            // notify end of stream
            inputBufferId = decoder.dequeueInputBuffer(-1);
            if (inputBufferId < 0) {
                throw new IllegalStateException("dequeueInputBuffer return " + inputBufferId);
            }
            decoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);

            // get decoded image
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            while (true) {
                int outputBufferId = decoder.dequeueOutputBuffer(bufferInfo, -1);
                if (outputBufferId >= 0) {
                    decoder.releaseOutputBuffer(outputBufferId, true);
                    break;
                } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outputFormat = decoder.getOutputFormat();
                    Log.d(TAG, "HEVC output-format=" + outputFormat);
                } else {
                    Log.d(TAG, "HEVC dequeueOutputBuffer return " + outputBufferId);
                }
            }
            decoder.flush();
        } finally {
            decoder.stop();
            decoder.release();
        }
        long endTime = SystemClock.elapsedRealtimeNanos();
        Log.i(TAG, "HEVC decoding elapsed=" + (endTime - beginTime) / 1000000.f + "[msec]");
    }

    private static Bitmap convertToBitmap(Image image) {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new RuntimeException("unsupported image format(" + image.getFormat() + ")");
        }
        RenderScript rs = mRenderScript;
        final int width = image.getWidth();
        final int height = image.getHeight();

        // prepare input Allocation for RenderScript
        Type.Builder inType = new Type.Builder(rs, Element.U8(rs)).setX(width).setY(height).setYuvFormat(ImageFormat.YV12);
        Allocation inAlloc = Allocation.createTyped(rs, inType.create(), Allocation.USAGE_SCRIPT);
        byte[] rawBuffer = new byte[inAlloc.getBytesSize()];
        int lumaSize = width * height;
        int chromaSize = (width / 2) * (height / 2);
        Image.Plane[] planes = image.getPlanes();
        planes[0].getBuffer().get(rawBuffer, 0, lumaSize);
        planes[1].getBuffer().get(rawBuffer, lumaSize, chromaSize);
        planes[2].getBuffer().get(rawBuffer, lumaSize + chromaSize, chromaSize);
        inAlloc.copyFromUnchecked(rawBuffer);

        // prepare output Allocation for RenderScript
        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Allocation outAlloc = Allocation.createFromBitmap(rs, bmp, Allocation.MipmapControl.MIPMAP_NONE, Allocation.USAGE_SCRIPT | Allocation.USAGE_SHARED);

        // convert YUV to RGB colorspace
        ScriptC_yuv2rgb converter = new ScriptC_yuv2rgb(rs);
        converter.set_gYUV(inAlloc);
        converter.forEach_convert(outAlloc);
        outAlloc.copyTo(bmp);
        return bmp;
    }

    private static class ImageInfo {
        Size size;
        ByteBuffer paramset;
        int offset;
        int length;
    }
}