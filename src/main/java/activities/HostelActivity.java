package activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import models.HostelRequest;
import models.LibraryRequest;
import models.Student;

public class HostelActivity extends AppCompatActivity {

    private EditText etLocation;
    private Spinner spRoomType;
    private CheckBox cbSharable;
    private Student studentData;
    private LibraryRequest libraryData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        studentData = getIntent().getParcelableExtra("STUDENT_DATA");
        libraryData = getIntent().getParcelableExtra("LIBRARY_DATA");

        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(48, 48, 48, 48);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Step 3: Hostel Booking");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        mainLayout.addView(tvTitle);

        etLocation = new EditText(this);
        etLocation.setHint("Hostel Location");
        mainLayout.addView(etLocation);

        spRoomType = new Spinner(this);
        spRoomType.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Bedsitter", "1 Bedroom", "Single Room"}));
        mainLayout.addView(spRoomType);

        cbSharable = new CheckBox(this);
        cbSharable.setText("Sharable Room");
        mainLayout.addView(cbSharable);

        Button btnNext = new Button(this);
        btnNext.setText("Next: MPESA Payment");
        btnNext.setBackgroundColor(Color.parseColor("#007BFF"));
        btnNext.setTextColor(Color.WHITE);
        mainLayout.addView(btnNext);

        scrollView.addView(mainLayout);
        setContentView(scrollView);

        btnNext.setOnClickListener(v -> {
            HostelRequest hostelRequest = new HostelRequest();
            hostelRequest.setLocation(etLocation.getText().toString());
            hostelRequest.setRoomType(spRoomType.getSelectedItem().toString());
            hostelRequest.setSharable(cbSharable.isChecked());

            Intent intent = new Intent(HostelActivity.this, PaymentActivity.class);
            intent.putExtra("STUDENT_DATA", studentData);
            intent.putExtra("LIBRARY_DATA", libraryData);
            intent.putExtra("HOSTEL_DATA", hostelRequest);
            startActivity(intent);
        });
    }
}