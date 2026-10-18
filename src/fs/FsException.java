package fs;

/**
 * 文件系统逻辑错误。
 */
public class FsException extends RuntimeException {
    public FsException(String message) {
        super(message);
    }
}
