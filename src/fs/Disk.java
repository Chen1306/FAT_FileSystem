package fs;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

/**
 * 用一个主机文件模拟整块磁盘，按 64 字节盘块随机读写。
 */
public class Disk {
    private static final long IMAGE_SIZE = (long) BootBlock.TOTAL_BLOCKS * BootBlock.BLOCK_SIZE;

    private final RandomAccessFile file;
    private final boolean created;
    private final byte[] buffer1 = new byte[BootBlock.BLOCK_SIZE];
    private final byte[] buffer2 = new byte[BootBlock.BLOCK_SIZE];

    public Disk(String imagePath) throws IOException {
        Path path = Paths.get(imagePath);
        boolean pathExists = Files.exists(path);
        long size = pathExists ? Files.size(path) : 0;
        boolean validExisting = pathExists && size > 0;
        if (validExisting && size != IMAGE_SIZE) {
            throw new IOException("disk.img 大小必须是 8192 字节");
        }

        file = new RandomAccessFile(imagePath, "rw");
        created = !validExisting;
        if (created) {
            file.setLength(IMAGE_SIZE);
        }
    }

    public boolean isCreated() {
        return created;
    }

    public byte[] readBlock(int blockNo) throws IOException {
        checkBlock(blockNo);
        file.seek((long) blockNo * BootBlock.BLOCK_SIZE);
        file.readFully(buffer1);
        return Arrays.copyOf(buffer1, BootBlock.BLOCK_SIZE);
    }

    public void writeBlock(int blockNo, byte[] data) throws IOException {
        checkBlock(blockNo);
        if (data.length != BootBlock.BLOCK_SIZE) {
            throw new IOException("盘块数据长度必须为 64 字节");
        }
        System.arraycopy(data, 0, buffer2, 0, BootBlock.BLOCK_SIZE);
        file.seek((long) blockNo * BootBlock.BLOCK_SIZE);
        file.write(buffer2);
    }

    public void close() throws IOException {
        file.close();
    }

    private static void checkBlock(int blockNo) throws IOException {
        if (blockNo < 0 || blockNo >= BootBlock.TOTAL_BLOCKS) {
            throw new IOException("非法盘块号: " + blockNo);
        }
    }
}
