package com.example.laba5;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends AppCompatActivity {

    private EditText journalIdInput;
    private Button downloadButton, viewButton, deleteButton;
    private String filePath;

    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String KEY_DONT_SHOW_AGAIN = "dontShowAgain";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        journalIdInput = findViewById(R.id.journalIdInput);
        downloadButton = findViewById(R.id.downloadButton);
        viewButton = findViewById(R.id.viewButton);
        deleteButton = findViewById(R.id.deleteButton);

        downloadButton.setOnClickListener(v -> downloadFile());
        viewButton.setOnClickListener(v -> viewFile());
        deleteButton.setOnClickListener(v -> deleteFile());
    }

    @Override
    protected void onStart() {
        super.onStart();

        // Проверяем, нужно ли показывать всплывающее окно
        SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        boolean dontShowAgain = sharedPreferences.getBoolean(KEY_DONT_SHOW_AGAIN, false);

        if (!dontShowAgain) {
            // Отложенный показ PopupWindow
            new Handler().postDelayed(this::showPopupWindow, 100);
        }
    }

    private void downloadFile() {
        // Получаем значение из EditText
        String journalId = journalIdInput.getText().toString().trim();
        if (journalId.isEmpty()) {
            Toast.makeText(this, "Введите идентификатор группы", Toast.LENGTH_SHORT).show();
            return;
        }

        // Формируем URL для скачивания файла
        String url = "https://cchgeu.ru/upload/iblock/58c/ugz36odld1trvc8o9d8p1wz4haaruh0n/" + journalId + ".xls";
        new DownloadFileTask().execute(url);
    }

    private class DownloadFileTask extends AsyncTask<String, Void, Boolean> {
        private String errorMessage;

        @Override
        protected Boolean doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setDoOutput(true);
                connection.connect();

                int responseCode = connection.getResponseCode();
                String contentType = connection.getContentType();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    if (contentType != null && contentType.equals("application/vnd.ms-excel")) {
                        InputStream inputStream = connection.getInputStream();
                        File directory = new File(getExternalFilesDir(null), "DownloadedFiles");
                        if (!directory.exists()) {
                            directory.mkdirs();
                        }
                        // Используем journalId для создания имени файла
                        String journalId = urls[0].substring(urls[0].lastIndexOf('/') + 1, urls[0].lastIndexOf('.'));
                        File file = new File(directory, journalId + ".xls");
                        FileOutputStream outputStream = new FileOutputStream(file);
                        byte[] buffer = new byte[1024];
                        int bytesRead;
                        while ((bytesRead = inputStream.read(buffer)) != -1) {
                            outputStream.write(buffer, 0, bytesRead);
                        }
                        outputStream.close();
                        inputStream.close();
                        filePath = file.getAbsolutePath();
                        return true;
                    } else {
                        errorMessage = "Файл не найден или недоступен.";
                        return false;
                    }
                } else {
                    errorMessage = "Ошибка загрузки файла: " + responseCode;
                    return false;
                }
            } catch (IOException e) {
                errorMessage = "Ошибка: " + e.getMessage();
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success) {
                viewButton.setVisibility(View.VISIBLE);
                deleteButton.setVisibility(View.VISIBLE);
                Toast.makeText(MainActivity.this, "Файл успешно загружен", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, errorMessage, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void viewFile() {
        if (filePath != null) {
            File file = new File(filePath);
            Uri uri = FileProvider.getUriForFile(this, getApplicationContext().getPackageName() + ".provider", file);
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(uri, "application/vnd.ms-excel");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                Toast.makeText(this, "Нет приложения для открытия Excel файлов", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void deleteFile() {
        if (filePath != null) {
            File file = new File(filePath);
            if (file.exists() && file.delete()) {
                Toast.makeText(this, "Файл удален", Toast.LENGTH_SHORT).show();
                viewButton.setVisibility(View.GONE);
                deleteButton.setVisibility(View.GONE);
                filePath = null;
            } else {
                Toast.makeText(this, "Ошибка при удалении файла", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void showPopupWindow() {
        // Проверяем, что активность не завершена
        if (isFinishing()) {
            return; // Не показываем PopupWindow, если активность завершена
        }

        // Создаем PopupWindow
        LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
        View popupView = inflater.inflate(R.layout.popup_window, null);

        PopupWindow popupWindow = new PopupWindow(popupView,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);

        // Находим элементы в popup
        CheckBox dontShowAgainCheckbox = popupView.findViewById(R.id.dontShowAgainCheckbox);
        Button okButton = popupView.findViewById(R.id.okButton);

        // Устанавливаем обработчик нажатия на кнопку OK
        okButton.setOnClickListener(v -> {
            if (dontShowAgainCheckbox.isChecked()) {
                // Сохраняем состояние чекбокса
                SharedPreferences sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putBoolean(KEY_DONT_SHOW_AGAIN, true);
                editor.apply();
            }
            popupWindow.dismiss();
        });

        // Отображаем PopupWindow
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);

        // Проверяем, что активность не завершена
        if (!isFinishing()) {
            popupWindow.showAtLocation(findViewById(android.R.id.content), Gravity.CENTER, 0, 0);
        }
    }
}