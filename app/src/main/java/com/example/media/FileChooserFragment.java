/*
 * Copyright 2022 A2va
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.media;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;

import androidx.core.app.ActivityCompat;
import androidx.fragment.app.DialogFragment;

import android.provider.OpenableColumns;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;


public class FileChooserFragment extends DialogFragment {

    private final static String TAG = "FileChooserFragment";

    private static final int REQUEST_CODE_PERMISSION = 1000;
    private static final int RESULT_CODE_FILECHOOSER = 2000;

    private Button buttonBrowse;
    private Button buttonOK;
    private Button buttonCancel;
    private EditText editTextPath;

    private String choosedFilename;
    private Uri choosedUri = null;

    private FileChooserListener listener;

    /**
     * Create fragment view.
     *
     * @param inflater
     * @param container
     * @param savedInstanceState
     * */
    @Override
    public View onCreateView(final LayoutInflater inflater, final ViewGroup container, final Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_file_chooser, container, false);

        editTextPath = rootView.findViewById(R.id.editText_path);
        buttonBrowse = rootView.findViewById(R.id.button_browse);
        buttonOK = rootView.findViewById(R.id.button_ok);
        buttonCancel = rootView.findViewById(R.id.button_cancel);

        buttonBrowse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                askPermissionAndBrowseFile();
            }

        });

        buttonOK.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().getSupportFragmentManager().beginTransaction().remove(FileChooserFragment.this).commit();
                if(listener == null) {
                    return;
                }

                if(choosedUri == null) {
                    listener.onFileChoosed(editTextPath.getText().toString(), null);
                    return;
                }

                listener.onFileChoosed(choosedFilename, choosedUri);
            }
        });


        buttonCancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                getActivity().getSupportFragmentManager().beginTransaction().remove(FileChooserFragment.this).commit();
            }
        });

        return rootView;
    }

    /**
     * Ask permission to access to storage and browse file.
     * */
    private void askPermissionAndBrowseFile()  {
        // With Android Level >= 23, you have to ask the user
        // for permission to access External Storage.
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) { // Level 23

            // Check if we have Call permission
            int permission = ActivityCompat.checkSelfPermission(this.getContext(),
                    Manifest.permission.READ_EXTERNAL_STORAGE);

            if (permission != PackageManager.PERMISSION_GRANTED) {
                // If don't have permission so prompt the user.
                this.requestPermissions(
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_CODE_PERMISSION
                );
                return;
            }
        }
        doBrowseFile();
    }

    /**
     * Start intent to browse file.
     * */
    private void doBrowseFile()  {

        Intent chooseFileIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        chooseFileIntent.setType("*/*");
        // Only return URIs that can be opened with ContentResolver
        chooseFileIntent.addCategory(Intent.CATEGORY_OPENABLE);

        chooseFileIntent = Intent.createChooser(chooseFileIntent, "Choose a file");
        startActivityForResult(chooseFileIntent, RESULT_CODE_FILECHOOSER);
    }

    /**
     * Result of requesting permissions.
     *
     * @param requestCode
     * @param permissions
     * @param grantResults
     * */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           String permissions[], int[] grantResults) {

        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case REQUEST_CODE_PERMISSION: {
                // Note: If request is cancelled, the result arrays are empty.
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i( TAG,"Permission granted!");
                    Toast.makeText(this.getContext(), "Permission granted!", Toast.LENGTH_SHORT).show();

                    doBrowseFile();
                }
                // Cancelled or denied.
                else {
                    Log.i(TAG,"Permission denied!");
                    Toast.makeText(this.getContext(), "Permission denied!", Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    /**
     * Result of activity result.
     *
     * @param requestCode
     * @param resultCode
     * @param data
     * */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case RESULT_CODE_FILECHOOSER:
                if (resultCode == Activity.RESULT_OK) {
                    if(data != null)  {
                        Uri fileUri = data.getData();
                        Log.i(TAG, "Uri: " + fileUri);

                        Cursor returnCursor = getContext().getContentResolver().query(fileUri, null, null, null, null);
                        returnCursor.moveToFirst();


                        // Get filename and displays it in the text view
                        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        String filename = returnCursor.getString(nameIndex);
                        editTextPath.setText(filename);

                        choosedFilename =  filename;
                        choosedUri = fileUri;




                    }
                }
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    /**
     * Set file selected listener.
     *
     * @param listener
     * */
    public void setFileChoosedListener(FileChooserListener listener) {
        this.listener = listener;
    }

    public interface FileChooserListener {
        /**
         * Listener when a file is selected.
         *
         * @param url filename or url
         * @param uri uri of selected file
         */
        public void onFileChoosed(String url, Uri uri);
    }
}