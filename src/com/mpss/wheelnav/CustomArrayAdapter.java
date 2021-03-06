package com.mpss.wheelnav;

import java.text.SimpleDateFormat;
import java.util.Locale;

import com.mpss.wheelnav.R;
import com.mpss.wheelnav.model.DatabaseHandler;
import com.mpss.wheelnav.model.IssueRequest;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.LayoutParams;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.TextView;

public class CustomArrayAdapter extends ArrayAdapter<String> {
	private final Context context;
	private final String[] values;

	public CustomArrayAdapter(Context context, String[] values) {
		super(context, R.layout.activity_requests_list_view, values);
		this.context = context;
		this.values = values;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View rowView = inflater.inflate(R.layout.layout_list_view_cell, parent, false);
		
		rowView.setLayoutParams(new LayoutParams(parent.getWidth(), parent.getHeight()/5)); 
		
		DatabaseHandler handler = new DatabaseHandler(context);
		IssueRequest request = handler.getRequest(Integer.parseInt(values[position]));
		
		ImageView imageView = (ImageView) rowView.findViewById(R.id.imageView);
		Bitmap bitmap = BitmapFactory.decodeFile(request.get_annotatedImageFilePath());
		imageView.setScaleType(ScaleType.FIT_XY);
		imageView.setImageBitmap(bitmap);
		//imageView.setLayoutParams(new android.widget.LinearLayout.LayoutParams(rowView.getWidth(), rowView.getHeight()));
		
		TextView textViewIssueType = (TextView) rowView.findViewById(R.id.label_issueType);
		
		String issueType = request.get_issueType().trim();
		
		if (issueType.equals("Type1")) {
			textViewIssueType.setText(context.getString(R.string.accessibility_issue_type_1));
		}
		else if (issueType.equals("Type2")) {
			textViewIssueType.setText(context.getString(R.string.accessibility_issue_type_2));
		}
		else if (issueType.equals("Type3")) {
			textViewIssueType.setText(context.getString(R.string.accessibility_issue_type_3));
		}
		else if (issueType.equals("Type4")) {
			textViewIssueType.setText(context.getString(R.string.accessibility_issue_type_4));
		}
		else if (issueType.equals("Type5")) {
			textViewIssueType.setText(context.getString(R.string.accessibility_issue_type_5));
		}
		else {
			textViewIssueType.setText("NA");
		}

		TextView textViewLat = (TextView) rowView.findViewById(R.id.label_lat);
		textViewLat.setText("Latitude: " + request.get_locationLatitude());

		TextView textViewLong = (TextView) rowView.findViewById(R.id.label_long);
		textViewLong.setText("Longitude: " + request.get_locationLongitude());
		
		TextView textViewDateCaptured = (TextView) rowView.findViewById(R.id.label_dateCaptured);
		textViewDateCaptured.setText("Time: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(request.get_DateCaptured()));

		TextView textViewStatus = (TextView) rowView.findViewById(R.id.label_status);
		if(request.is_UploadedToServer()) {
			textViewStatus.setText("Status: Uploaded");
		}
		else {
			textViewStatus.setText("Status: Failed To Upload");
		}

		
		return rowView;
	}
}
