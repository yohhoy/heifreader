package jp.yohhoy.heifreader.iso14496.part12;

import org.mp4parser.support.AbstractFullBox;
import org.mp4parser.tools.IsoTypeReader;

import java.nio.ByteBuffer;

/*
 * <h1>4cc = "{@value #TYPE}"</h1>
 */
public final class PrimaryItemBox extends AbstractFullBox {
    public static final String TYPE = "pitm";

    private int itemId;

    public PrimaryItemBox() {
        super(TYPE);
    }

    public int getItemId() {
        return itemId;
    }

    @Override
    public void _parseDetails(ByteBuffer content) {
        parseVersionAndFlags(content);
        itemId = IsoTypeReader.readUInt16(content);
    }

    @Override
    public long getContentSize() {
        return 10;
    }

    @Override
    public void getContent(ByteBuffer byteBuffer) {
        throw new RuntimeException(TYPE + " not implemented");
    }

    @Override
    public String toString() {
        return "PrimaryItemBox[itemId=" + itemId + "]";
    }
}
