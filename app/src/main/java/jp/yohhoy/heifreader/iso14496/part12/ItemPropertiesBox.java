package jp.yohhoy.heifreader.iso14496.part12;

import org.mp4parser.BoxParser;
import org.mp4parser.support.AbstractBox;
import org.mp4parser.support.AbstractContainerBox;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;

/*
 * <h1>4cc = "{@value #TYPE}"</h1>
 */
public final class ItemPropertiesBox extends AbstractContainerBox {
    public static final String TYPE = "iprp";

    public ItemPropertiesBox() {
        super(TYPE);
    }

    @Override
    public void parse(ReadableByteChannel dataSource, ByteBuffer header, long contentSize, BoxParser boxParser) throws IOException {
        initContainer(dataSource, contentSize, boxParser);
        for (AbstractBox box : this.getBoxes(AbstractBox.class)) {
            box.parseDetails();
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
