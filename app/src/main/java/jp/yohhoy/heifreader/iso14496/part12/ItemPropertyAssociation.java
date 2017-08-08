package jp.yohhoy.heifreader.iso14496.part12;

import org.mp4parser.support.AbstractFullBox;
import org.mp4parser.tools.IsoTypeReader;

import java.nio.ByteBuffer;

/*
 * <h1>4cc = "{@value #TYPE}"</h1>
 */
public class ItemPropertyAssociation extends AbstractFullBox {
    public static final String TYPE = "ipma";

    private long contentSize;

    public ItemPropertyAssociation() {
        super(TYPE);
    }

    @Override
    public void _parseDetails(ByteBuffer content) {
        contentSize = content.limit();

        parseVersionAndFlags(content);
        long entry_count = IsoTypeReader.readUInt32(content);
        for (long i = 0; i < entry_count; i++) {
            long item_ID;
            if (getVersion() < 1) {
                item_ID = IsoTypeReader.readUInt16(content);
            } else {
                item_ID = IsoTypeReader.readUInt32(content);
            }
            int association_count = IsoTypeReader.readUInt8(content);
            for (int j = 0; j < association_count; j++) {
                int essential_property_index;
                if ((getFlags() & 1) == 1) {
                    essential_property_index = IsoTypeReader.readUInt16(content);
                } else {
                    essential_property_index = IsoTypeReader.readUInt8(content);
                }
            }
        }
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
        return "ItemPropertyAssociation";
    }
}
