package capstone.kookmin.sksss.test2;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.ArrayAdapter;

public class CustomAutoCompleteTextChangedListener implements TextWatcher {

	public static final String TAG = "CustomAutoCompleteTextChangedListener.java";
	Context context;

	public CustomAutoCompleteTextChangedListener(Context context) {
		this.context = context;
	}


	@Override
	public void afterTextChanged(Editable s) {

	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {

	}

	@Override
	public void onTextChanged(CharSequence userInput, int start, int before,
			int count) {

		Log.e(TAG, "User input: " + userInput);

		SoftKeyboard keyboard = ((SoftKeyboard) context);

		keyboard.item = keyboard.getItemsFromDb(userInput.toString());
//		keyboard.myAdapter.notifyDataSetChanged();
//		keyboard.myAdapter = new ArrayAdapter<String>(keyboard,
//				android.R.layout.simple_dropdown_item_1line, keyboard.item);
//		keyboard.myAutoComplete.setAdapter(keyboard.myAdapter);
	}
}