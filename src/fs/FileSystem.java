package fs;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 文件系统核心层。最小原型实现目录创建/列出、文件创建/写入/查看。
 */
public class FileSystem {
    private final Disk disk;
    private final FatTable fat;
    private final OFT oft = new OFT();

    public FileSystem(String imagePath) throws IOException {
        disk = new Disk(imagePath);
        fat = new FatTable(disk);
        if (disk.isCreated()) {
            fat.initialize();
            initializeRoot();
        } else {
            fat.load();
        }
    }

    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > 3) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean letter = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
            boolean digit = c >= '0' && c <= '9';
            if (!letter && !digit) {
                return false;
            }
        }
        return true;
    }

    public void md(String path) throws IOException {
        path = normalizePath(path);
        if (path.equals("/")) {
            throw new FsException("不能创建根目录");
        }
        String parent = parentPath(path);
        String name = leafName(path);
        int parentBlockNo = locateDirectoryBlock(parent);
        byte[] parentBlock = disk.readBlock(parentBlockNo);
        if (findEntry(parentBlock, name) >= 0) {
            throw new FsException("同名对象已存在: " + name);
        }
        int freeIndex = findFreeEntry(parentBlock);
        if (freeIndex < 0) {
            throw new FsException("目录已满，最多 8 项");
        }
        int blockNo = fat.allocate();
        if (blockNo < 0) {
            throw new FsException("磁盘无空闲盘块");
        }
        byte[] newDirBlock = new byte[BootBlock.BLOCK_SIZE];
        for (int i = 0; i < BootBlock.ENTRIES_PER_BLOCK; i++) {
            DirItem.clear(newDirBlock, i * BootBlock.DIR_ENTRY_SIZE);
        }
        disk.writeBlock(blockNo, newDirBlock);
        new DirItem(name, BootBlock.ATTR_DIRECTORY, blockNo, 0)
                .encode(parentBlock, freeIndex * BootBlock.DIR_ENTRY_SIZE);
        disk.writeBlock(parentBlockNo, parentBlock);
    }

    public List<DirItem> listDirectory(String path) throws IOException {
        int blockNo = locateDirectoryBlock(path);
        byte[] block = disk.readBlock(blockNo);
        List<DirItem> items = new ArrayList<>();
        for (int i = 0; i < BootBlock.ENTRIES_PER_BLOCK; i++) {
            int offset = i * BootBlock.DIR_ENTRY_SIZE;
            if (!DirItem.isEmpty(block, offset)) {
                items.add(DirItem.decode(block, offset));
            }
        }
        return items;
    }

    public void createFile(String path) throws IOException {
        path = normalizePath(path);
        if (path.equals("/")) {
            throw new FsException("非法文件名");
        }
        String parent = parentPath(path);
        String name = leafName(path);
        int parentBlockNo = locateDirectoryBlock(parent);
        byte[] parentBlock = disk.readBlock(parentBlockNo);
        if (findEntry(parentBlock, name) >= 0) {
            throw new FsException("同名对象已存在: " + name);
        }
        int freeIndex = findFreeEntry(parentBlock);
        if (freeIndex < 0) {
            throw new FsException("目录已满，最多 8 项");
        }
        int blockNo = fat.allocate();
        if (blockNo < 0) {
            throw new FsException("磁盘无空闲盘块");
        }
        byte[] fileBlock = new byte[BootBlock.BLOCK_SIZE];
        Arrays.fill(fileBlock, (byte) '#');
        disk.writeBlock(blockNo, fileBlock);
        new DirItem(name, BootBlock.ATTR_NORMAL, blockNo, 1)
                .encode(parentBlock, freeIndex * BootBlock.DIR_ENTRY_SIZE);
        disk.writeBlock(parentBlockNo, parentBlock);
    }

    public void openFile(String path, int flag) throws IOException {
        path = normalizePath(path);
        if (flag != OFT.READ && flag != OFT.WRITE) {
            throw new FsException("非法打开方式");
        }
        EntryLoc loc = locate(path);
        if (loc.item.isDirectory()) {
            throw new FsException("不能打开目录: " + path);
        }
        if (flag == OFT.WRITE && loc.item.isReadOnly()) {
            throw new FsException("只读文件不能以写方式打开: " + path);
        }
        OFT.OFTLE existing = oft.find(path);
        if (existing != null) {
            if (existing.flag == flag) {
                return;
            }
            if (existing.flag == OFT.READ && flag == OFT.WRITE) {
                existing.flag = OFT.WRITE;
                existing.write = findWritePointer(existing.number);
                return;
            }
            throw new FsException("文件已以写方式打开，请先关闭: " + path);
        }
        if (oft.count() >= BootBlock.MAX_OPEN_FILES) {
            throw new FsException("打开文件数已达上限（5 个）");
        }
        OFT.OFTLE entry = new OFT.OFTLE(path, loc.item.getAttribute(),
                loc.item.getStartBlock(), loc.item.getLength(), flag);
        entry.read = new OFT.Pointer(loc.item.getStartBlock(), 0);
        entry.write = flag == OFT.WRITE
                ? findWritePointer(loc.item.getStartBlock())
                : new OFT.Pointer(loc.item.getStartBlock(), 0);
        oft.add(entry);
    }

    public boolean isOpen(String path) {
        return oft.isOpen(normalizePath(path));
    }

    public void writeFile(String path, String content) throws IOException {
        path = normalizePath(path);
        if (content == null) {
            content = "";
        }
        if (content.indexOf('#') >= 0) {
            throw new FsException("内容不能包含结束标记 '#'");
        }
        OFT.OFTLE entry = oft.find(path);
        if (entry == null) {
            throw new FsException("文件未打开: " + path);
        }
        if (entry.flag != OFT.WRITE) {
            throw new FsException("文件不是以写方式打开: " + path);
        }

        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        OFT.Pointer ptr = entry.write;
        int available = (BootBlock.BLOCK_SIZE - ptr.bnum)
                + fat.countFree() * BootBlock.BLOCK_SIZE;
        if (available < bytes.length + 1) {
            throw new FsException("磁盘空间不足，无法写入");
        }

        int block = ptr.dnum;
        int offset = ptr.bnum;
        int index = 0;
        while (index < bytes.length) {
            if (offset == BootBlock.BLOCK_SIZE) {
                int next = fat.get(block);
                if (next == BootBlock.FAT_EOF) {
                    next = fat.allocate();
                    if (next < 0) {
                        throw new FsException("磁盘无空闲盘块");
                    }
                    fat.set(block, next);
                    fat.save();
                    byte[] fill = new byte[BootBlock.BLOCK_SIZE];
                    Arrays.fill(fill, (byte) '#');
                    disk.writeBlock(next, fill);
                } else if (next < 3 || next >= BootBlock.TOTAL_BLOCKS
                        || next == BootBlock.FAT_FREE || next == BootBlock.FAT_BAD) {
                    throw new FsException("文件 FAT 链损坏");
                }
                block = next;
                offset = 0;
            }
            byte[] data = disk.readBlock(block);
            while (offset < BootBlock.BLOCK_SIZE && index < bytes.length) {
                data[offset++] = bytes[index++];
            }
            disk.writeBlock(block, data);
        }
        ptr.dnum = block;
        ptr.bnum = offset;
    }

    public void closeFile(String path) throws IOException {
        path = normalizePath(path);
        OFT.OFTLE entry = oft.find(path);
        if (entry == null) {
            return;
        }
        if (entry.flag == OFT.WRITE) {
            OFT.Pointer ptr = entry.write;
            int block = ptr.dnum;
            int offset = ptr.bnum;
            if (offset == BootBlock.BLOCK_SIZE) {
                int next = fat.get(block);
                if (next == BootBlock.FAT_EOF) {
                    next = fat.allocate();
                    if (next < 0) {
                        throw new FsException("磁盘空间不足，无法写入结束标记");
                    }
                    fat.set(block, next);
                    fat.save();
                    byte[] fill = new byte[BootBlock.BLOCK_SIZE];
                    Arrays.fill(fill, (byte) '#');
                    disk.writeBlock(next, fill);
                } else if (next < 3 || next >= BootBlock.TOTAL_BLOCKS
                        || next == BootBlock.FAT_FREE || next == BootBlock.FAT_BAD) {
                    throw new FsException("文件 FAT 链损坏");
                }
                block = next;
                offset = 0;
            }
            byte[] data = disk.readBlock(block);
            data[offset] = '#';
            disk.writeBlock(block, data);

            EntryLoc loc = locate(path);
            loc.item.setLength(fat.chainLength(entry.number));
            loc.item.encode(loc.parentBlock, loc.index * BootBlock.DIR_ENTRY_SIZE);
            disk.writeBlock(loc.parentBlockNo, loc.parentBlock);
        }
        oft.remove(path);
    }

    public String readFile(String path, int length) throws IOException {
        path = normalizePath(path);
        if (length < 0) {
            throw new FsException("读取长度不能为负数");
        }
        OFT.OFTLE entry = oft.find(path);
        if (entry == null) {
            throw new FsException("文件未打开: " + path);
        }
        if (entry.flag != OFT.READ) {
            throw new FsException("文件不是以读方式打开: " + path);
        }
        if (length == 0) {
            return "";
        }

        OFT.Pointer ptr = entry.read;
        int block = ptr.dnum;
        int offset = ptr.bnum;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        boolean[] visited = new boolean[BootBlock.TOTAL_BLOCKS];

        while (out.size() < length) {
            if (offset == BootBlock.BLOCK_SIZE) {
                int next = fat.get(block);
                if (next < 3 || next >= BootBlock.TOTAL_BLOCKS
                        || next == BootBlock.FAT_EOF || next == BootBlock.FAT_BAD
                        || next == BootBlock.FAT_FREE) {
                    break;
                }
                if (visited[next]) {
                    throw new FsException("文件 FAT 链损坏");
                }
                block = next;
                offset = 0;
            }
            if (block < 3 || block >= BootBlock.TOTAL_BLOCKS || visited[block]) {
                throw new FsException("文件 FAT 链损坏");
            }
            visited[block] = true;
            byte[] data = disk.readBlock(block);
            while (offset < BootBlock.BLOCK_SIZE && out.size() < length) {
                byte b = data[offset];
                if (b == '#') {
                    ptr.dnum = block;
                    ptr.bnum = offset;
                    return new String(out.toByteArray(), StandardCharsets.UTF_8);
                }
                out.write(b);
                offset++;
            }
        }
        ptr.dnum = block;
        ptr.bnum = offset;
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    public String typeFile(String path) throws IOException {
        path = normalizePath(path);
        EntryLoc loc = locate(path);
        if (loc.item.isDirectory()) {
            throw new FsException("不是文件: " + path);
        }
        if (oft.isOpen(path)) {
            throw new FsException("文件已打开，无法查看: " + path);
        }
        return new String(readAllBytes(loc.item.getStartBlock()), StandardCharsets.UTF_8);
    }

    public void closeAllOpenFiles() throws IOException {
        while (oft.count() > 0) {
            closeFile(oft.get(0).name);
        }
    }

    public void close() throws IOException {
        try {
            closeAllOpenFiles();
        } finally {
            disk.close();
        }
    }

    private void initializeRoot() throws IOException {
        byte[] block = new byte[BootBlock.BLOCK_SIZE];
        for (int i = 0; i < BootBlock.ENTRIES_PER_BLOCK; i++) {
            DirItem.clear(block, i * BootBlock.DIR_ENTRY_SIZE);
        }
        disk.writeBlock(BootBlock.ROOT_BLOCK, block);
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty() || path.charAt(0) != '/') {
            throw new FsException("必须使用绝对路径: " + path);
        }
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (path.equals("/")) {
            return path;
        }
        String[] parts = path.substring(1).split("/", -1);
        for (String part : parts) {
            if (!isValidName(part)) {
                throw new FsException("非法路径或名称: " + path);
            }
        }
        return path;
    }

    private static String parentPath(String path) {
        int index = path.lastIndexOf('/');
        return index == 0 ? "/" : path.substring(0, index);
    }

    private static String leafName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    private int locateDirectoryBlock(String path) throws IOException {
        path = normalizePath(path);
        if (path.equals("/")) {
            return BootBlock.ROOT_BLOCK;
        }
        EntryLoc loc = locate(path);
        if (!loc.item.isDirectory()) {
            throw new FsException("不是目录: " + path);
        }
        return loc.item.getStartBlock();
    }

    private EntryLoc locate(String path) throws IOException {
        path = normalizePath(path);
        if (path.equals("/")) {
            throw new FsException("根目录没有父目录项");
        }
        String[] parts = path.substring(1).split("/", -1);
        int dirBlockNo = BootBlock.ROOT_BLOCK;
        for (int i = 0; i < parts.length; i++) {
            byte[] block = disk.readBlock(dirBlockNo);
            int index = findEntry(block, parts[i]);
            if (index < 0) {
                throw new FsException("文件或目录不存在: " + path);
            }
            DirItem item = DirItem.decode(block, index * BootBlock.DIR_ENTRY_SIZE);
            if (i == parts.length - 1) {
                return new EntryLoc(block, dirBlockNo, index, item);
            }
            if (!item.isDirectory()) {
                throw new FsException("路径中间项不是目录: " + parts[i]);
            }
            dirBlockNo = item.getStartBlock();
        }
        throw new FsException("非法路径: " + path);
    }

    private static int findEntry(byte[] block, String name) {
        for (int i = 0; i < BootBlock.ENTRIES_PER_BLOCK; i++) {
            int offset = i * BootBlock.DIR_ENTRY_SIZE;
            if (DirItem.isEmpty(block, offset)) {
                continue;
            }
            DirItem item = DirItem.decode(block, offset);
            if (item.getName().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static int findFreeEntry(byte[] block) {
        for (int i = 0; i < BootBlock.ENTRIES_PER_BLOCK; i++) {
            if (DirItem.isEmpty(block, i * BootBlock.DIR_ENTRY_SIZE)) {
                return i;
            }
        }
        return -1;
    }

    private OFT.Pointer findWritePointer(int startBlock) throws IOException {
        int block = startBlock;
        boolean[] visited = new boolean[BootBlock.TOTAL_BLOCKS];
        while (block >= 3 && block < BootBlock.TOTAL_BLOCKS && !visited[block]) {
            visited[block] = true;
            byte[] data = disk.readBlock(block);
            for (int i = 0; i < BootBlock.BLOCK_SIZE; i++) {
                if (data[i] == '#') {
                    return new OFT.Pointer(block, i);
                }
            }
            int next = fat.get(block);
            if (next < 3 || next >= BootBlock.TOTAL_BLOCKS
                    || next == BootBlock.FAT_EOF || next == BootBlock.FAT_BAD
                    || next == BootBlock.FAT_FREE) {
                return new OFT.Pointer(block, BootBlock.BLOCK_SIZE);
            }
            block = next;
        }
        throw new FsException("文件 FAT 链损坏");
    }

    private byte[] readAllBytes(int startBlock) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int block = startBlock;
        boolean[] visited = new boolean[BootBlock.TOTAL_BLOCKS];
        while (block >= 3 && block < BootBlock.TOTAL_BLOCKS && !visited[block]) {
            visited[block] = true;
            byte[] data = disk.readBlock(block);
            for (int i = 0; i < BootBlock.BLOCK_SIZE; i++) {
                if (data[i] == '#') {
                    return out.toByteArray();
                }
                out.write(data[i]);
            }
            int next = fat.get(block);
            if (next < 3 || next >= BootBlock.TOTAL_BLOCKS
                    || next == BootBlock.FAT_EOF || next == BootBlock.FAT_BAD
                    || next == BootBlock.FAT_FREE) {
                break;
            }
            block = next;
        }
        return out.toByteArray();
    }

    private static class EntryLoc {
        final byte[] parentBlock;
        final int parentBlockNo;
        final int index;
        final DirItem item;

        EntryLoc(byte[] parentBlock, int parentBlockNo, int index, DirItem item) {
            this.parentBlock = parentBlock;
            this.parentBlockNo = parentBlockNo;
            this.index = index;
            this.item = item;
        }
    }
}
