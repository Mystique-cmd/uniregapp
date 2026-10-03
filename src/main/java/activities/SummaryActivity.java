package activities;

import android.content.Context;
import android.os.Bundle;
import android.print.PrintManager;
import android.util.TypedValue;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import models.Student;

public class SummaryActivity extends AppCompatActivity {

    private WebView printWebView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Student studentData = getIntent().getParcelableExtra("STUDENT_DATA");

        LinearLayout mainLayout = new LinearLayout(this);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setPadding(48, 48, 48, 48);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Registration Complete");
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        mainLayout.addView(tvTitle);

        TextView tvSummary = new TextView(this);
        tvSummary.setText("Name: " + studentData.getFullName() + "\nID: " + studentData.getIdNo() + "\nStatus: Payment Confirmed");
        tvSummary.setPadding(0, 32, 0, 32);
        mainLayout.addView(tvSummary);

        Button btnPrint = new Button(this);
        btnPrint.setText("Print Summary Document");
        mainLayout.addView(btnPrint);

        setContentView(mainLayout);

        btnPrint.setOnClickListener(v -> generatePrintDocument(studentData));
    }

    private void generatePrintDocument(Student student) {
        WebView webView = new WebView(this);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                createWebPrintJob(view);
                printWebView = null;
            }
        });

        // Generate HTML for the print layout
        String htmlDocument = "<html><body><h1>University Registration Summary</h1>" +
                "<p><strong>Name:</strong> " + student.getFullName() + "</p>" +
                "<p><strong>Registration No:</strong> " + student.getRegNumber() + "</p>" +
                "<p><em>Generated automatically upon confirmed offline/online sync.</em></p>" +
                "</body></html>";

        webView.loadDataWithBaseURL(null, htmlDocument, "text/HTML", "UTF-8", null);
        printWebView = webView; // Keep a reference to prevent garbage collection
    }

    private void createWebPrintJob(WebView webView) {
        PrintManager printManager = (PrintManager) this.getSystemService(Context.PRINT_SERVICE);
        String jobName = getString(android.R.string.untitled) + " Registration_Summary";
        
        printManager.print(jobName, webView.createPrintDocumentAdapter(jobName), null);
    }
}