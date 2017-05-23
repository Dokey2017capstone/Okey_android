package capstone.kookmin.sksss.test2;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.AutoCompleteTextView;

public class CustomAutoCompleteView extends android.support.v7.widget.AppCompatAutoCompleteTextView {

	public CustomAutoCompleteView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
	}

	public CustomAutoCompleteView(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub
	}

	public CustomAutoCompleteView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void performFiltering(final CharSequence text, final int keyCode) {
		String filterText = "";
			super.performFiltering(filterText, keyCode);
	}

	@Override
	protected void replaceText(final CharSequence text) {
		super.replaceText(text);
	}

}