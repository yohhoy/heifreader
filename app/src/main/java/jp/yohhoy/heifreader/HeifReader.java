package jp.yohhoy.heifreader;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.SystemClock;
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
import java.io.IOException;
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

/*
 * HEIF Reader
 */
public class HeifReader {
    private static final String TAG = "HeifReader";

    /*
     * load HEIF image
     */
    public static Size loadHeif(byte[] heif, Surface surface) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(heif);
        IsoFile isoFile = new IsoFile(Channels.newChannel(bais));
        ImageInfo info = parseHeif(isoFile);
        ByteBuffer bitstream = extractBitstream(heif, info);
        renderHevcImage(bitstream, info, surface);
        return info.size;
    }

    private static class ImageInfo {
        Size size;
        ByteBuffer paramset;
        int offset;
        int length;
    }

    private static ImageInfo parseHeif(IsoFile isoFile) throws IOException {
        // validate brand compatibility ('ftyp' box)
        List<FileTypeBox> ftypBoxes = isoFile.getBoxes(FileTypeBox.class);
        if (ftypBoxes.size() != 1) {
            throw new IOException("FileTypeBox('ftyp') shall be unique");
        }
        FileTypeBox ftypBox = ftypBoxes.get(0);
        Log.d(TAG, "HEIC ftyp=" + ftypBox);
        if (!"mif1".equals(ftypBox.getMajorBrand())|| ftypBox.getCompatibleBrands().indexOf("heic") < 0) {
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
        info.size = new Size((int)ispeBox.display_width, (int)ispeBox.display_height);
        Log.i(TAG, "HEIC image size=" + ispeBox.display_width + "x" + ispeBox.display_height);

        // get HEVC decoder configuration
        HevcConfigurationBox hvccBox = findBox(primaryPropBoxes, HevcConfigurationBox.class);
        if (hvccBox == null) {
            throw new IOException("HevcConfigurationBox('hvcC') not found");
        }
        HevcDecoderConfigurationRecord hevcConfig = hvccBox.getHevcDecoderConfigurationRecord();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final byte[] startCode = { 0x00, 0x00, 0x00, 0x01 };
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
                Log.d(TAG, "codec \"" + name + "\" not found", ex);
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
}