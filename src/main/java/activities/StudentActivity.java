package activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import models.Student;

public class StudentActivity extends AppCompatActivity {

    private EditText etFullName, etIdNo, etRegNumber;
    private RadioGroup rgGender;
    private Spinner spCourse, spDepartment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(48, 48, 48, 48);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Step 1: Student Registration");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        tvTitle.setPadding(0, 0, 0, 32);
        mainLayout.addView(tvTitle);

        etFullName = createEditText("Full Names");
        etIdNo = createEditText("ID Number");
        etRegNumber = createEditText("Registration Number");
        mainLayout.addView(etFullName);
        mainLayout.addView(etIdNo);
        mainLayout.addView(etRegNumber);

        rgGender = new RadioGroup(this);
        rgGender.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbMale = new RadioButton(this);
        rbMale.setText("Male");
        RadioButton rbFemale = new RadioButton(this);
        rbFemale.setText("Female");
        rgGender.addView(rbMale);
        rgGender.addView(rbFemale);
        mainLayout.addView(rgGender);

        spCourse = new Spinner(this);
        spCourse.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Computer Science", "Software Engineering"}));
        mainLayout.addView(spCourse);

        spDepartment = new Spinner(this);
        spDepartment.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new String[]{"Computer Science", "Electrical Engineering"}));
        spDepartment.setPadding(0, 32, 0, 48);
        mainLayout.addView(spDepartment);

        Button btnNext = new Button(this);
        btnNext.setText("Next: Library Services");
        btnNext.setBackgroundColor(Color.parseColor("#007BFF"));
        btnNext.setTextColor(Color.WHITE);
        mainLayout.addView(btnNext);

        scrollView.addView(mainLayout);
        setContentView(scrollView);

        btnNext.setOnClickListener(v -> {
            if (etFullName.getText().toString().isEmpty()) {
                Toast.makeText(this, "Name required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Assume Student implements Parcelable
            Student student = new Student();
            student.setFullName(etFullName.getText().toString());
            student.setIdNo(etIdNo.getText().toString());
            // Populate other fields...

            Intent intent = new Intent(StudentActivity.this, LibraryActivity.class);
            intent.putExtra("STUDENT_DATA", student);
            startActivity(intent);
        });
    }

    private EditText createEditText(String hint) {
        EditText editText = new EditText(this);
        editText.setHint(hint);
        editText.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        editText.setPadding(0, 24, 0, 24);
        return editText;
    }
}