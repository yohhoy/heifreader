package jp.yohhoy.heifreader.iso23008.part12;

import org.mp4parser.support.AbstractFullBox;
import org.mp4parser.tools.IsoTypeReader;

import java.nio.ByteBuffer;

/*
 * <h1>4cc = "{@value #TYPE}"</h1>
 */
public class ImageSpatialExtentsBox extends AbstractFullBox {
    public static final String TYPE = "ispe";

    public long display_width;
    public long display_height;

    public ImageSpatialExtentsBox() {
        super(TYPE);
    }

    @Override
    public void _parseDetails(ByteBuffer content) {
        parseVersionAndFlags(content);
        display_width = IsoTypeReader.readUInt32(content);
        display_height = IsoTypeReader.readUInt32(content);
    }

    @Override
    public long getContentSize() {
        return 16;
    }

    @Override
    public void getContent(ByteBuffer byteBuffer) {
        throw new RuntimeException(TYPE + " not implemented");
    }

    @Override
    public String toString() {
        return "ImageSpatialExtentsBox[" + display_width + "x" + display_height + "]";
    }
}
