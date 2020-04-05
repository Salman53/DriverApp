package com.example.uberapp;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.rengwuxian.materialedittext.MaterialEditText;

public class RegisterActivity extends AppCompatActivity {

   private MaterialEditText edtEmail , edtPassword , edtName , edtPhoneNumber;
   private Button btnRegister;
   private FirebaseAuth firebaseAuth;
   private FirebaseDatabase firebaseDatabase;
   private DatabaseReference databaseReference;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        firebaseAuth = firebaseAuth.getInstance();
        firebaseDatabase = FirebaseDatabase.getInstance();
        databaseReference = firebaseDatabase.getReference(Common.user_driver_tbl);


        initializeFields();

        btnRegister.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                creatAccount();
            }
        });


    }

    private void creatAccount() {

        if(TextUtils.isEmpty(edtEmail.getText().toString()))
        {
            edtEmail.setError("Enter Email");
            return;

        }

        if(TextUtils.isEmpty(edtPhoneNumber.getText().toString()))
        {
            edtPhoneNumber.setError("Enter Phone Number");
            return;
        }

        if(edtPassword.getText().toString().length() < 6)
        {
            edtPassword.setError("Password Too Short");
            return;
        }

        if(TextUtils.isEmpty(edtName.getText().toString()))
        {
            edtName.setError("Enter Name");
            return;
        }

        firebaseAuth.createUserWithEmailAndPassword(edtEmail.getText().toString() , edtPassword.getText().toString())
                .addOnSuccessListener(new OnSuccessListener<AuthResult>() {
                    @Override
                    public void onSuccess(AuthResult authResult) {
                        User user = new User();
                        user.setEmail(edtEmail.getText().toString());
                        user.setName(edtName.getText().toString());
                        user.setPassword(edtPassword.getText().toString());
                        user.setPhone(edtPhoneNumber.getText().toString());

                        databaseReference.child(FirebaseAuth.getInstance().getCurrentUser().getUid())

                                .setValue(user)
                                .addOnSuccessListener(new OnSuccessListener<Void>() {
                                    @Override
                                    public void onSuccess(Void aVoid) {
                                        Toast.makeText(RegisterActivity.this, "Registered Sucessfully", Toast.LENGTH_SHORT).show();
                                        startActivity(new Intent(RegisterActivity.this , MapsActivity.class));
                                        finish();
                                    }
                                })
                                .addOnFailureListener(new OnFailureListener() {
                                    @Override
                                    public void onFailure(@NonNull Exception e) {
                                        Toast.makeText(RegisterActivity.this, "Failed"+e.getMessage(), Toast.LENGTH_SHORT).show();
                                    }
                                });

                    }
                });
    }

    private void initializeFields() {
        edtEmail = findViewById(R.id.materialEditTextEmail);
        edtName = findViewById(R.id.materialEditTextName);
        edtPassword = findViewById(R.id.materialEditTextPassword);
        edtPhoneNumber = findViewById(R.id.materialEditTextPhoneNumber);
        btnRegister = findViewById(R.id.btnRegister);
    }
}
