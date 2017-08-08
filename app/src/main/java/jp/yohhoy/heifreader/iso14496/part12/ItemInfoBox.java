package jp.yohhoy.heifreader.iso14496.part12;

import org.mp4parser.BoxParser;
import org.mp4parser.support.AbstractContainerBox;
import org.mp4parser.tools.IsoTypeReader;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/*
 * <h1>4cc = "{@value #TYPE}"</h1>
 */
public final class ItemInfoBox extends AbstractContainerBox {
    public static final String TYPE = "iinf";

    private int version;
    private int flags;

    public ItemInfoBox() {
        super(TYPE);
    }

    @Override
    public void parse(ReadableByteChannel dataSource, ByteBuffer header, long contentSize, BoxParser boxParser) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(6);
        dataSource.read(buffer);
        buffer.rewind();
        version = IsoTypeReader.readUInt8(buffer);
        flags = IsoTypeReader.readUInt24(buffer);
        //entryCount = IsoTypeReader.readUInt16(buffer);
        initContainer(dataSource, contentSize - 6, boxParser);

        for (ItemInfoEntry entry : getBoxes(ItemInfoEntry.class)) {
            entry.parseDetails();
        }
    }

    @Override
    public void getBox(WritableByteChannel writableByteChannel) throws IOException {
        throw new RuntimeException(TYPE + " not implemented");
    }

    @Override
    public long getSize() {
        long s = getContainerSize();
        long t = 6;
        return s + t + ((largeBox || (s + t + 8) >= (1L << 32)) ? 16 : 8);
    }
}