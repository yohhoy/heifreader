package jp.yohhoy.heifreader.iso14496.part12;

import org.mp4parser.support.AbstractFullBox;
import org.mp4parser.tools.IsoTypeReader;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/*
 * <h1>4cc = "{@value #TYPE}"</h1>
 */
public class ItemPropertyAssociation extends AbstractFullBox {
    public static final String TYPE = "ipma";

    private long contentSize;
    private List<Item> items = new ArrayList<>();

    public ItemPropertyAssociation() {
        super(TYPE);
    }

    static public class Assoc {
        public int essential;
        public int property_index;
    }

    static public class Item {
        public long item_ID;
        public List<Assoc> associations = new ArrayList<>();
    }

    public List<Item> getItems() {
        return items;
    }

    @Override
    public void _parseDetails(ByteBuffer content) {
        contentSize = content.limit();

        parseVersionAndFlags(content);
        long entry_count = IsoTypeReader.readUInt32(content);
        for (long i = 0; i < entry_count; i++) {
            Item item = new Item();
            if (getVersion() < 1) {
                item.item_ID = IsoTypeReader.readUInt16(content);
            } else {
                item.item_ID = IsoTypeReader.readUInt32(content);
            }
            int association_count = IsoTypeReader.readUInt8(content);
            for (int j = 0; j < association_count; j++) {
                Assoc assoc = new Assoc();
                int value, indexLength;
                if ((getFlags() & 1) == 1) {
                    value = IsoTypeReader.readUInt16(content);
                    indexLength = 15;
                } else {
                    value = IsoTypeReader.readUInt8(content);
                    indexLength = 7;
                }
                assoc.essential = value >> indexLength;
                assoc.property_index = value & ((1 << indexLength) - 1);
                item.associations.add(assoc);
            }
            items.add(item);
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
