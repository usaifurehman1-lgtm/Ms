package com.offlinemesh.chat;

import android.content.Context;
import android.net.Uri;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileTransferManager {

    private static final int PORT = 8990;
    private static final int BUFFER_SIZE = 8192;
    private static final long MAX_FILE_SIZE = 100L * 1024 * 1024;

    private final Context context;

    private final ExecutorService executor =
            Executors.newCachedThreadPool();

    public interface Callback {
        void onProgress(long transferred, long total);
        void onSuccess(File file);
        void onError(String error);
    }

    public FileTransferManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public void sendFile(
            String host,
            Uri uri,
            Callback callback
    ) {
        executor.execute(() -> {

            try {
                InputStream source =
                        context.getContentResolver()
                                .openInputStream(uri);

                if (source == null) {
                    throw new IOException("Cannot open selected file");
                }

                try (InputStream input = source;
                     Socket socket = new Socket(host, PORT);
                     DataOutputStream output =
                             new DataOutputStream(
                                     new BufferedOutputStream(
                                             socket.getOutputStream()
                                     )
                             )) {

                    String name = "shared_file";

                    try (android.database.Cursor cursor =
                                 context.getContentResolver()
                                         .query(uri, null, null, null, null)) {

                        if (cursor != null &&
                                cursor.moveToFirst()) {

                            int index = cursor.getColumnIndex(
                                    android.provider.OpenableColumns.DISPLAY_NAME
                            );

                            if (index >= 0) {
                                name = cursor.getString(index);
                            }
                        }
                    }

                    long total = -1;

                    try (android.database.Cursor cursor =
                                 context.getContentResolver()
                                         .query(uri, null, null, null, null)) {

                        if (cursor != null &&
                                cursor.moveToFirst()) {

                            int index = cursor.getColumnIndex(
                                    android.provider.OpenableColumns.SIZE
                            );

                            if (index >= 0) {
                                total = cursor.getLong(index);
                            }
                        }
                    }

                    if (total > MAX_FILE_SIZE) {
                        throw new IOException("File exceeds 100 MB limit");
                    }

                    output.writeUTF(name);
                    output.writeLong(total);
                    output.flush();

                    byte[] buffer = new byte[BUFFER_SIZE];

                    long transferred = 0;
                    int count;

                    while ((count = input.read(buffer)) != -1) {

                        transferred += count;

                        if (transferred > MAX_FILE_SIZE) {
                            throw new IOException("File exceeds size limit");
                        }

                        output.write(buffer, 0, count);
                        output.flush();

                        if (callback != null) {
                            callback.onProgress(transferred, total);
                        }
                    }

                    output.flush();

                    if (callback != null) {
                        callback.onSuccess(null);
                    }
                }

            } catch (Exception e) {

                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    public void receiveFile(Callback callback) {

        executor.execute(() -> {

            try (java.net.ServerSocket server =
                         new java.net.ServerSocket(PORT)) {

                try (Socket socket = server.accept();
                     DataInputStream input =
                             new DataInputStream(
                                     new BufferedInputStream(
                                             socket.getInputStream()
                                     )
                             )) {

                    String name = input.readUTF();

                    long total = input.readLong();

                    if (total < -1 || total > MAX_FILE_SIZE) {
                        throw new IOException("Invalid file size");
                    }

                    name = new File(name).getName();

                    if (name.isEmpty() || name.contains("..")) {
                        throw new IOException("Invalid file name");
                    }

                    File directory = new File(
                            context.getFilesDir(),
                            "received"
                    );

                    if (!directory.exists() && !directory.mkdirs()) {
                        throw new IOException("Cannot create directory");
                    }

                    File destination = new File(directory, name);

                    if (destination.exists()) {
                        destination = new File(
                                directory,
                                System.currentTimeMillis() + "_" + name
                        );
                    }

                    File temporary = new File(
                            directory,
                            destination.getName() + ".part"
                    );

                    long received = 0;

                    try (FileOutputStream output =
                                 new FileOutputStream(temporary)) {

                        byte[] buffer = new byte[BUFFER_SIZE];

                        int count;

                        while (total < 0 || received < total) {

                            int wanted = total < 0
                                    ? buffer.length
                                    : (int) Math.min(
                                            buffer.length,
                                            total - received
                                    );

                            count = input.read(buffer, 0, wanted);

                            if (count == -1) {
                                break;
                            }

                            received += count;

                            if (received > MAX_FILE_SIZE) {
                                throw new IOException("File too large");
                            }

                            output.write(buffer, 0, count);

                            if (callback != null) {
                                callback.onProgress(received, total);
                            }
                        }

                        output.flush();
                    }

                    if (total >= 0 && received != total) {
                        temporary.delete();
                        throw new IOException("Incomplete file received");
                    }

                    if (!temporary.renameTo(destination)) {
                        temporary.delete();
                        throw new IOException("Could not save received file");
                    }

                    if (callback != null) {
                        callback.onSuccess(destination);
                    }
                }

            } catch (Exception e) {

                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
    }

    public void close() {
        executor.shutdownNow();
    }
}
