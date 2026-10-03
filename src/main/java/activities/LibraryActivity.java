package activities;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import models.LibraryRequest;
import models.Student;

public class LibraryActivity extends AppCompatActivity {

    private EditText etIsbn, etAuthor, etTitle, etDateBorrowed, etDateReturned;
    private Student studentData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        studentData = getIntent().getParcelableExtra("STUDENT_DATA");

        ScrollView scrollView = new ScrollView(this);
        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(48, 48, 48, 48);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Step 2: Library Registration");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        mainLayout.addView(tvTitle);

        etIsbn = new EditText(this); etIsbn.setHint("Book ISBN");
        etAuthor = new EditText(this); etAuthor.setHint("Author");
        etTitle = new EditText(this); etTitle.setHint("Title");
        etDateBorrowed = new EditText(this); etDateBorrowed.setHint("Date Borrowed (YYYY-MM-DD)");
        etDateReturned = new EditText(this); etDateReturned.setHint("Date of Return (YYYY-MM-DD)");

        mainLayout.addView(etIsbn);
        mainLayout.addView(etAuthor);
        mainLayout.addView(etTitle);
        mainLayout.addView(etDateBorrowed);
        mainLayout.addView(etDateReturned);

        Button btnNext = new Button(this);
        btnNext.setText("Next: Hostel Booking");
        btnNext.setBackgroundColor(Color.parseColor("#007BFF"));
        btnNext.setTextColor(Color.WHITE);
        mainLayout.addView(btnNext);

        scrollView.addView(mainLayout);
        setContentView(scrollView);

        btnNext.setOnClickListener(v -> {
            LibraryRequest libRequest = new LibraryRequest();
            libRequest.setBookIsbn(etIsbn.getText().toString());
            // Populate other library fields...

            Intent intent = new Intent(LibraryActivity.this, HostelActivity.class);
            intent.putExtra("STUDENT_DATA", studentData);
            intent.putExtra("LIBRARY_DATA", libRequest);
            startActivity(intent);
        });
    }
}