/**
 * ******************************************************************************************
 * Copyright (C) 2014 - Food and Agriculture Organization of the United Nations
 * (FAO). All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,this
 * list of conditions and the following disclaimer. 2. Redistributions in binary
 * form must reproduce the above copyright notice,this list of conditions and
 * the following disclaimer in the documentation and/or other materials provided
 * with the distribution. 3. Neither the name of FAO nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT,STRICT LIABILITY,OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING
 * IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 * *********************************************************************************************
 */
package org.sola.common;

import java.awt.*;
import java.io.*;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import jakarta.activation.DataHandler;
import jakarta.activation.FileDataSource;
import org.apache.commons.io.FileUtils;
import org.sola.common.messaging.ClientMessage;
import org.sola.common.messaging.MessageUtility;
import org.sola.common.messaging.ServiceMessage;

/**
 * Provides static methods to manage various aspects related to the files.
 *
 * The FileUtility also maintains a cache of documents and will automatically
 * purge old files from the cache if the cache exceeds its maximum size (default
 * max size is 200Mb).
 */
public class FileUtility {

    public final static String CSV = "csv";
    // Ticket #397 - use !! to delimit the location of path separators as an
    // alternative to the default ;. This is because SOLA uses ; as a special
    // path separator character when dealing with NetworkFolder. 
    public final static String ALTERNATE_PATH_SEPARATOR = "!!";
    private static long maxCacheSizeBytes = 200L * 1024 * 1024;
    private static long resizedCacheSizeBytes = 120L * 1024 * 1024;
    private static int minNumberCachedFiles = 10;
    private static long maxFileSizeBytes = 100L * 1024 * 1024;
    private static String cachePath = System.getProperty("user.home") + "/sola/cache/documents/";
    
    /**
     * Checks the cache to ensure it won't exceed the max size cache size. If
     * the new document will cause the cache to exceed the max size, the older
     * documents in the cache are deleted until the cache reaches the resize
     * limit.
     *
     * @param cache The directory for the documents cache
     * @param newFileSize The size of the new file to open in bytes.
     */
    private static void maintainCache(File cache, long newFileSize) {
        long cacheSize = getDirectorySize(cache, false);
        cacheSize += newFileSize;
        if (cacheSize > maxCacheSizeBytes) {
            System.out.println("Resizing SOLA documents cache.");
            // The cache has exceeded its max size. Delete the oldest files in the cache based
            // on thier last modified date. 
            List<File> files = Arrays.asList(cache.listFiles());
            Collections.sort(files, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    return (f1.lastModified() > f2.lastModified() ? 1
                            : (f1.lastModified() == f2.lastModified() ? 0 : -1));
                }
            });

            int numFiles = files.size();
            for (File f : files) {
                if (numFiles < minNumberCachedFiles) {
                    break;
                }
                // Only delete files - ignore subdirectories. 
                if (f.isFile()) {
                    cacheSize = cacheSize - f.length();
                    f.delete();
                    if (cacheSize < resizedCacheSizeBytes) {
                        break;
                    }
                }
                numFiles--;
            }
        }
    }

    /**
     * Sets the minimum number of files that should be left in the cache when it
     * is being resized. Default is 10.
     */
    public static void setMinNumberCachedFiles(int num) {
        minNumberCachedFiles = num;
    }

    /**
     * The target size of the cache in bytes after a resize/maintenance is
     * performed. Default is 120MB.
     *
     * @param sizeInBytes The target size of the cache in bytes.
     */
    public static void setResizedCacheSizeBytes(long sizeInBytes) {
        resizedCacheSizeBytes = sizeInBytes;
    }

    /**
     * The maximum size of the cache in bytes. Default is 200MB
     *
     * @param sizeInBytes The maximum size of the cache in bytes.
     */
    public static void setMaxCacheSizeBytes(long sizeInBytes) {
        maxCacheSizeBytes = sizeInBytes;
    }

    /**
     * The maximum size of a file (in bytes) that can be loaded into SOLA.
     * Default is 100MB.
     *
     * <p>
     * SOLA uses a file streaming service to upload and download files to and
     * from the client. The file streaming service streams files directly to
     * disk and does not store them in memory allowing the SOLA client
     * application to potentially handle files of any size. However, be aware
     * that files must be completely loaded into memory by the Digital Archive
     * Service before they can be saved to the SOLA database. Increasing this
     * value from its default may require adjusting the memory settings for the
     * SOLA domain on the SOLA Glassfish Server. </p>
     *
     * @param sizeInBytes The maximum size of the file in bytes.
     */
    public static void setMaxFileSizeBytes(long sizeInBytes) {
        maxFileSizeBytes = sizeInBytes;
    }

    /**
     * Sets the path to use for the documents cache
     *
     * @param newCachePath The new cache path.
     */
    public static void setCachePath(String newCachePath) {
        if (newCachePath != null) {
            cachePath = newCachePath;
        }
    }

    /**
     * Returns the absolute file path for the documents cache directory.
     */
    public static String getCachePath() {
        File cache = new File(cachePath);
        if (!cache.exists()) {
            // Need to create the file cache directory. 
            cache.mkdirs();
        }
        return cachePath;
    }

    /**
     * Returns true if the file to check is already in the documents cache. Note
     * that the document name should include the rowVersion number to ensure any
     * documents that get updated also get reloaded in the cache.
     *
     * @param tmpFileName The name of the file to check in the documents cache.
     */
    public static boolean isCached(String tmpFileName) {
        tmpFileName = sanitizeFileName(tmpFileName, true);
        File file = new File(getCachePath() + File.separator + tmpFileName);
        return file.exists();
    }

    /**
     * Returns the byte array for the file. The default maximum size of a file
     * to load is 100MB. This can be modified using
     * {@linkplain #setMaxFileSizeBytes(long)}
     *
     * @param filePath The full path to the file
     */
    public static byte[] getFileBinary(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            return null;
        }
        if (file.length() > maxFileSizeBytes) {
            DecimalFormat df = new DecimalFormat("#,###.#");
            String maxFileSizeMB = df.format(maxFileSizeBytes / (1024 * 1024));
            String fileSizeMB = df.format(file.length() / (1024 * 1024));
            throw new SOLAException(ServiceMessage.EXCEPTION_FILE_TOO_BIG,
                    new String[]{fileSizeMB, maxFileSizeMB});
        }
        try {
            return readFile(file);
        } catch (IOException ex) {
            throw new SOLAException(ServiceMessage.GENERAL_UNEXPECTED_ERROR_DETAILS,
                    new String[]{"File could not be read", ex.getLocalizedMessage()});
        }
    }

    /**
     * Returns file's extention.
     *
     * @param fileName The name of the file.
     */
    public static String getFileExtension(String fileName) {
        String ext = null;
        if (fileName.lastIndexOf(".") > 0 && fileName.lastIndexOf(".") < fileName.length()) {
            ext = fileName.substring(fileName.lastIndexOf(".") + 1);
        }
        return ext;
    }

    /**
     * Returns file name excluding extention.
     *
     * @param fileName The name of the file.
     */
    public static String getFileNameWithoutExtension(String fileName) {
        String name = fileName;
        if (fileName.lastIndexOf(".") > 0 && fileName.lastIndexOf(".") < fileName.length()) {
            name = fileName.substring(0, fileName.lastIndexOf("."));
        }
        return name;
    }

    /*
     * Get the extension of a file.
     */
    public static String getFileExtension(File f) {
        String ext = null;
        String s = f.getName();
        int i = s.lastIndexOf('.');

        if (i > 0 && i < s.length() - 1) {
            ext = s.substring(i + 1).toLowerCase();
        }
        return ext;
    }

    /**
     * Returns the size of the directory. This is done by summing the size of
     * each file in the directory. The sizes of all subdirectories can be
     * optionally included.
     *
     * @param directory The directory to calculate the size for.
     */
    public static long getDirectorySize(File directory, boolean recursive) {
        long length = 0;
        if (!directory.isFile()) {
            for (File file : directory.listFiles()) {
                if (file.isFile()) {
                    length += file.length();
                } else {
                    if (recursive) {
                        length += getDirectorySize(file, recursive);
                    }
                }

            }
        }
        return length;
    }

    /**
     * Opens the specified file from the documents cache. If the file does not
     * exist in the cache a File Open exception is thrown.
     *
     * @param tmpFileName The name of the file to open from the documents cache.
     */
    public static void openFile(String tmpFileName) {
        String fileName = sanitizeFileName(tmpFileName, true);
        openFile(new File(getCachePath() + File.separator + fileName));
    }

    /**
     * Creates a new file in the documents cache using the fileBinary data then
     * opens the file for display.
     *
     * @param fileBinary The binary content of the file to open.
     * @param fileName The name to use for creating the file. This name must
     * exclude any file path.
     */
    public static void openFile(byte[] fileBinary, String fileName) {
        File file = writeFileToCache(fileBinary, fileName);
        openFile(file);
    }

    /**
     * Opens the file from the documents cache using the Java Desktop.
     *
     * @param file The file to open
     * @throws SOLAException Failed to open file
     */
    public static void openFile(File file) {
        if (file == null) {
            return;
        }
        String fileName = file.getName();
        if (isExecutable(fileName)) {
            // Make sure the extension is changed before opening the file. 
            fileName = setTmpExtension(fileName);
            File nonExeFile = new File(getCachePath() + File.separator + fileName);
            file.renameTo(nonExeFile);
            file = nonExeFile;
        }
        // Try to open the file. Need to check if the current platform has Java Desktop support and 
        // if so, whether the OPEN action is also supported. 
        boolean fileOpened = false;
        if (Desktop.isDesktopSupported()) {
            Desktop dt = Desktop.getDesktop();
            if (dt.isSupported(Desktop.Action.OPEN)) {
                try {
                    dt.open(file);
                    fileOpened = true;
                } catch (Exception ex) {
                    // The file could not be opened. The most likely cause is there is no editor
                    // installed for the file extension, but it may be due to file security 
                    // restrictions. Either way, inform the user they should open the file manually. 
                    fileOpened = false;
                }
            }
        }
        if (!fileOpened) {
            // The Java Desktop is not supported on this platform. Riase a mesage to 
            // tell the user they must manually open the document. 
            MessageUtility.displayMessage(ClientMessage.ERR_FAILED_OPEN_FILE,
                    new String[]{file.getAbsolutePath()});
        }
    }

    /**
     * Removes path separator characters (i.e. / and \) from the fileName. Used
     * to ensure user input does not redirect files to an unsafe locations. Also
     * replaces the extension for any file with an executable file extension
     * with .tmp if the replaceExtension parameter is true.
     *
     * @param fileName The fileName to sanitize.
     * @param replaceExtension If true, any executable extension on the file
     * will be replaced with .tmp
     * @see #isExecutable(java.lang.String)
     * @see #setTmpExtension(java.lang.String)
     */
    public static String sanitizeFileName(String fileName, boolean replaceExtension) {
        String result = fileName.replaceAll("\\\\|\\/", "#");
        if (isExecutable(result) && replaceExtension) {
            result = setTmpExtension(fileName);
        }
        return result;
    }

    /**
     * Checks if the file extension is considered to be an executable file
     * extension. Returns true if the extension is .exe, .msi, .bat, .cmd
     *
     * @param fileName The file name to check
     */
    public static boolean isExecutable(String fileName) {
        String extension = getFileExtension(fileName);
        boolean result = extension.equalsIgnoreCase("exe") || extension.equalsIgnoreCase("msi")
                || extension.equalsIgnoreCase("bat") || extension.equalsIgnoreCase("cmd");
        return result;
    }

    /**
     * Replaces the file extension with tmp. Note that the original file
     * extension is retained as part of the file name. e.g. file.exe becomes
     * file_exe.tmp
     *
     * @param fileName The file name to check.
     */
    public static String setTmpExtension(String fileName) {
        fileName = fileName.replaceAll("\\.", "_");
        return fileName + ".tmp";
    }

    /**
     * Generates a default file name using a random GUID as the primary file
     * name value.
     *
     * @see #generateFileName(java.lang.String, int, java.lang.String)
     * generateFileName
     */
    public static String generateFileName() {
        return generateFileName(java.util.UUID.randomUUID().toString(), 0, "tmp");
    }

    /**
     * Creates a versioned file name based on the document information.
     *
     * @param fileNr The number assigned to the document
     * @param rowVersion The rowversion of the document.
     * @param extension The file extension of the document.
     * @see #generateFileName()
     */
    public static String generateFileName(String fileNr, int rowVersion, String extension) {
        if (fileNr == null || extension == null || fileNr.isEmpty() || extension.isEmpty()) {
            return generateFileName();
        }
        String fileName = String.format("sola_%s_%s.%s", fileNr, rowVersion, extension);
        return sanitizeFileName(fileName, true);
    }


    /**
     * Creates a {@linkplain DataHandler} for a file located on the local file
     * system. The file can be loaded from any accessible location.
     *
     * @param fileName The name of the file to create the DataHandler for. If
     * the file is in the cache, only the file name is required. If the file is
     * located elsewhere, the full file pathname is required.
     */
    public static DataHandler getFileAsStream(String filePathName) {
        File file = new File(getCachePath() + File.separator + filePathName);
        if (!file.exists()) {
            file = new File(filePathName);
        }
        DataHandler result = null;
        if (file.exists()) {
            result = new DataHandler(new FileDataSource(file));
        } else {
            throw new SOLAException(ClientMessage.ERR_FAILED_OPEN_FILE,
                    new String[]{filePathName});
        }
        return result;
    }

    /**
     * Writes the file content to a file in the documents cache. The fileName is
     * sanitized before the new file is written. The new file name can be
     * obtained from the {@linkplain File#getName()} method.
     *
     * @param fileContent The content of the file to write to the file system
     * @param fileName The name to use for the new file. That file name may
     * change due to sanitization. If the fileName is null, a random file name
     * will be used.
     */
    public static File writeFileToCache(byte[] fileContent, String fileName) {
        if (fileContent == null) {
            return null;
        }
        if (fileName == null) {
            fileName = generateFileName();
        } else {
            fileName = sanitizeFileName(fileName, true);
        }
        File file = new File(getCachePath() + File.separator + fileName);
        try {
            // Check if the cache needs to have some documents purged
            maintainCache(new File(getCachePath()), fileContent.length);
            // Write the file to disk
            writeFile(new ByteArrayInputStream(fileContent), file);
        } catch (IOException iex) {
            Object[] lstParams = {fileName, iex.getLocalizedMessage()};
            throw new SOLAException(ClientMessage.ERR_FAILED_CREATE_NEW_FILE, lstParams);
        }
        return file;
    }

    /**
     * Reads a file in the documents cache into a byte array for further
     * processing.
     *
     * @param fileName THe name of the file to read. The fileName will be
     * sanitized.
     * @return The byte array representing the content of the file.
     */
    public static byte[] readFileFromCache(String fileName) {
        fileName = sanitizeFileName(fileName, true);
        File file = new File(getCachePath() + File.separator + fileName);
        try {
            return readFile(file);
        } catch (IOException ex) {
            throw new SOLAException(ServiceMessage.GENERAL_UNEXPECTED_ERROR_DETAILS,
                    new String[]{"File could not be read from cache", ex.getLocalizedMessage()});
        }
    }

    /**
     * Writes the data from an input stream to the specified file using buffered
     * 8KB chunks. This method closes the input stream once the write is
     * completed.
     *
     * @param in The InputStream to write
     * @param file The file to write the input stream to
     * @throws IOException If an IO error occurs while attempting to write the
     * file.
     */
    public static void writeFile(InputStream in, File file) throws IOException {
        if (file == null || in == null) {
            // Nothing to write
            return;
        }
        OutputStream out = null;
        try {
            deleteFile(file);
            file.setLastModified(DateUtility.now().getTime());
            out = new FileOutputStream(file);
            // Use an 8K buffer for writing the file. This is usually the most effecient 
            // buffer size. 
            byte[] buf = new byte[8 * 1024];
            int len;
            while ((len = in.read(buf)) != -1) {
                out.write(buf, 0, len);
            }
            out.flush();
        } finally {
            in.close();
            if (out != null) {
                out.close();
            }
        }
    }

    /**
     * Reads a file from the file system into a byte array.
     *
     * @param file The file to read.
     * @return Byte array representing the file content. Returns null if the
     * file does not exist.
     * @throws IOException
     */
    public static byte[] readFile(File file) throws IOException {
        byte[] result = null;
        if (file != null && file.exists()) {
            FileInputStream in=null;
            try {
                in = new FileInputStream(file);
                int length = (int) file.length();
                result = new byte[length];
                int offset = 0;
                int bytesRead = 1;
                // Attempt to slurp the entire file into the array in one go. Note that sometimes
                // read will not return all of the data, so need to add a while loop to continue
                // trying to read the reminaing bytes. If no bytes are read, exit the loop. 
                while (offset < length && bytesRead > 0) {
                    bytesRead = in.read(result, offset, (length - offset));
                    offset = bytesRead > 0 ? (offset + bytesRead) : offset;
                }
                if (offset < length) {
                    throw new SOLAException(ServiceMessage.GENERAL_UNEXPECTED_ERROR_DETAILS,
                            new Object[]{"File could not be read", file.getName()});
                }
            } finally {
                if (in!=null) {
                    in.close();
                }
            }
        }
        return result;
    }

    /**
     * Deletes the file from the file system if it exists.
     *
     * @param file The file to delete.
     */
    public static void deleteFile(File file) {
        if (file != null && file.exists()) {
            file.delete();
        }
    }

    /**
     * Deletes the file from the documents cache if it exists.
     *
     * @param fileName The name of the file to remove from the cache.
     */
    public static void deleteFileFromCache(String fileName) {
        fileName = sanitizeFileName(fileName, true);
        File file = new File(getCachePath() + File.separator + fileName);
        deleteFile(file);
    }

    public static String createFileFromContent(String fileName, String content)
            throws IOException {
        String filePath = getCachePath() + File.separator + sanitizeFileName(fileName, false);
        FileUtils.writeStringToFile(new File(filePath), content);
        return filePath;
    }

    /**
     * Deletes directory with all sub folders and files.
     *
     * @param file Directory to delete
     * @throws java.io.IOException
     */
    public static void deleteDirectory(File file) throws IOException {
        if (file.isDirectory()) {
            //directory is empty, delete it
            if (file.list().length == 0) {
                file.delete();
            } else {
                // loop through the files
                String files[] = file.list();

                for (String temp : files) {
                    File fileDelete = new File(file, temp);
                    //recursive delete
                    deleteDirectory(fileDelete);
                }

                //check the directory again, if empty then delete it
                if (file.list().length == 0) {
                    file.delete();
                }
            }

        } else {
            //if file, then delete it
            file.delete();
        }
    }

    /**
     * Formats file size, applying KB, MB, GB units.
     *
     * @param size Size to format
     * @return
     */
    public static String formatFileSize(long size) {
        if (size == 0) {
            return "0";
        }

        if (size < 1024) {
            return size + "B";
        }

        if (size >= 1024 && size < 1048576) {
            return Math.round((size / 1024) * 100.0) / 100.0 + "KB";
        }

        if (size >= 1048576 && size < 1073741824) {
            return Math.round((size / 1024 / 1024) * 100.0) / 100.0 + "MB";
        }

        if (size >= 1073741824 && size < 1099511627776L) {
            return Math.round((size / 1024 / 1024 / 1024) * 100.0) / 100.0 + "GB";
        }

        return Math.round((size / 1024 / 1024 / 1024 / 1024) * 100.0) / 100.0 + "TB";
    }
}
