package activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import database.FirebaseManager;
import payment.MpesaManager;
import models.Payment;
import models.Student;

public class PaymentActivity extends AppCompatActivity {

    private EditText etPhoneNumber, etAmount;
    private Student studentData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        studentData = getIntent().getParcelableExtra("STUDENT_DATA");
        // Retrieve other models similarly...

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(48, 48, 48, 48);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Step 4: MPESA Payment");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        mainLayout.addView(tvTitle);

        etPhoneNumber = new EditText(this);
        etPhoneNumber.setHint("M-PESA Phone Number (e.g. 2547...)");
        mainLayout.addView(etPhoneNumber);

        etAmount = new EditText(this);
        etAmount.setHint("Fees Amount (Ksh)");
        mainLayout.addView(etAmount);

        Button btnPay = new Button(this);
        btnPay.setText("Trigger M-PESA Push");
        btnPay.setBackgroundColor(Color.parseColor("#28A745")); // M-PESA Green
        btnPay.setTextColor(Color.WHITE);
        mainLayout.addView(btnPay);

        setContentView(mainLayout);

        btnPay.setOnClickListener(v -> {
            String phone = etPhoneNumber.getText().toString();
            String amount = etAmount.getText().toString();

            // Delegate API call to MpesaManager
            MpesaManager.initiateStkPush(phone, amount, new MpesaManager.PaymentCallback() {
                @Override
                public void onSuccess() {
                    Payment paymentData = new Payment(phone, amount, "Confirmed");
                    
                    // Push all structured data to Firebase using your manager
                    FirebaseManager.saveRegistrationRecord(studentData, paymentData);

                    Toast.makeText(PaymentActivity.this, "Payment Successful", Toast.LENGTH_SHORT).show();
                    
                    Intent intent = new Intent(PaymentActivity.this, SummaryActivity.class);
                    intent.putExtra("STUDENT_DATA", studentData);
                    startActivity(intent);
                    finish();
                }

                @Override
                public void onFailure(String error) {
                    Toast.makeText(PaymentActivity.this, "Payment Failed: " + error, Toast.LENGTH_LONG).show();
                }
            });
        });
    }
}