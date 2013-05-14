

package ru.david.manager;

import android.net.Uri;
import android.os.Bundle;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.TextView;
import android.widget.Button;
import android.widget.Toast;

public class HelpManager extends Activity implements OnClickListener {
	private static final String WEB = "http://vk.com/david_football";
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.help_layout);
		
		String text = "Курсовой проект\n" +
					  "Группа 10-ИТ-3\n"
					+ "Байлеванян Д.Г.\n";
		
		TextView label = (TextView)findViewById(R.id.help_top_label);
		label.setText(text);
		
		Button web = (Button)findViewById(R.id.help_website_bt);
		web.setOnClickListener(this);
	}

	@Override
	public void onClick(View view) {
		int id = view.getId();
		Intent i = new Intent();
		
		switch(id) {			
			case R.id.help_website_bt:
				i.setAction(android.content.Intent.ACTION_VIEW);
				i.setData(Uri.parse(WEB));
				try {
					startActivity(i);
					
				} catch(ActivityNotFoundException e) {
					Toast.makeText(this, "Извините, невозможно открыть вебсайт", Toast.LENGTH_SHORT).show();
				}
				break;
		}
	}

}
