package jp.yohhoy.heifreader.iso14496.part12;

import org.mp4parser.support.AbstractFullBox;
import org.mp4parser.tools.IsoTypeReader;

import java.nio.ByteBuffer;

/*
 * <h1>4cc = "{@value #TYPE}"</h1>
 */
public final class ItemInfoEntry extends AbstractFullBox {
    public static final String TYPE = "infe";

    private long contentSize;

    private int itemId;
    private int itemProtectionIndex;
    private String itemName;
    private String contentType;

    public ItemInfoEntry() {
        super(TYPE);
    }

    @Override
    public void _parseDetails(ByteBuffer content) {
        contentSize = content.limit();

        parseVersionAndFlags(content);
        itemId = IsoTypeReader.readUInt16(content);
        itemProtectionIndex = IsoTypeReader.readUInt16(content);
        itemName = IsoTypeReader.readString(content, 4);
        contentType = IsoTypeReader.readString(content);
        //if (0 < content.remaining()) {
        //    contentEncoding = IsoTypeReader.readString(content);
        //}
    }

    @Override
    public long getContentSize() {
        return contentSize;
    }

    @Override
    public void getContent(ByteBuffer byteBuffer) {
        throw new RuntimeException(TYPE + " not implemented");
    }

    @Override
    public String toString() {
        return "ItemInfoEntry[itemId=" + itemId + ";itemProtectionIndex=" + itemProtectionIndex
                + ";itemName=" + itemName + ";contentType=" + contentType + "]";
    }
}
