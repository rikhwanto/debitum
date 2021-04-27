package org.ebur.debitum.ui;

import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavBackStackEntry;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import org.ebur.debitum.R;
import org.ebur.debitum.Utilities;
import org.ebur.debitum.database.Person;
import org.ebur.debitum.database.Transaction;
import org.ebur.debitum.database.TransactionWithPerson;
import org.ebur.debitum.viewModel.EditTransactionViewModel;
import org.ebur.debitum.viewModel.PersonFilterViewModel;

import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

// https://medium.com/alexander-schaefer/implementing-the-new-material-design-full-screen-dialog-for-android-e9dcc712cb38
public class EditTransactionFragment extends DialogFragment implements AdapterView.OnItemSelectedListener {

    public static final String ARG_ID_TRANSACTION = "idTransaction";

    private EditTransactionViewModel viewModel;
    private PersonFilterViewModel personFilterViewModel;
    private NavController nav;

    private ArrayAdapter<String> nameSpinnerAdapter;

    private Toolbar toolbar;
    private Spinner spinnerNameView;
    private RadioButton gaveRadio;
    private EditText editAmountView;
    private SwitchCompat switchIsMonetaryView;
    private EditText editDescView;
    private TextView editDateView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.Theme_Debitum_FullScreenDialog);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container,
                             Bundle savedInstanceState) {

        viewModel = new ViewModelProvider(this).get(EditTransactionViewModel.class);
        personFilterViewModel = new ViewModelProvider(requireActivity()).get(PersonFilterViewModel.class);
        nav = NavHostFragment.findNavController(this);

        View root = inflater.inflate(R.layout.fragment_edit_transaction, container, false);

        // get Transaction ID from Arguments, which is also used to determine, if a new transaction is created
        viewModel.setIdTransaction(requireArguments().getInt(ARG_ID_TRANSACTION, -1));

        // setup views
        toolbar = root.findViewById(R.id.dialog_toolbar);
        spinnerNameView = root.findViewById(R.id.spinner_name);
        gaveRadio = root.findViewById(R.id.radioButton_gave);
        editAmountView = root.findViewById(R.id.edit_amount);
        editAmountView.addTextChangedListener(new AmountTextWatcher());
        editAmountView.addTextChangedListener(new AmountTextWatcher() { // control if Save-Button should be enabled
            @Override public void afterTextChanged(Editable s) {
                // using long here, because enforcing of max. 9 digits in
                // AmountTextWatcher.formatArbitraryAmount might not yet have taken place
                boolean amountOk = false;
                try {
                    amountOk = Long.parseLong(s.toString().replaceAll("[.,]", "")) > 0;
                } catch (NumberFormatException ignored) {
                } finally {
                    toolbar.getMenu().findItem(R.id.miSaveTransaction).setEnabled(amountOk);
                }

            }
        });
        switchIsMonetaryView = root.findViewById(R.id.switch_monetary);
        switchIsMonetaryView.setOnCheckedChangeListener(this::onSwitchIsMonetaryChanged);
        editDescView = root.findViewById(R.id.edit_description);
        editDateView = root.findViewById(R.id.edit_date);
        editDateView.setOnClickListener((view) -> showDatePickerDialog());

        // setup name spinner
        nameSpinnerAdapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_spinner_item);
        nameSpinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerNameView.setAdapter(nameSpinnerAdapter);
        spinnerNameView.setOnItemSelectedListener(this);

        //setHasOptionsMenu(true);

        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        toolbar.setNavigationOnClickListener(v -> dismiss());
        toolbar.inflateMenu(R.menu.menu_edit_transaction);
        toolbar.setOnMenuItemClickListener(this::onOptionsItemSelected);

        fillSpinnerNameView();
        prefillNameViewIfFromFilteredTransactionList();

        if (viewModel.isNewTransaction()) fillViewsNewTransaction();
        else fillViewsEditTransaction();
    }

    // make dialog fullscreen
    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null) {
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = ViewGroup.LayoutParams.MATCH_PARENT;
            dialog.getWindow().setLayout(width, height);
        }
    }

    private void fillSpinnerNameView() {
        // since an observed person-LiveData would be filled initially too late, we have to fill the adapter manually
        // this fixes a IllegalStateException in RecyclerView after completion
        try {
            for(Person person : viewModel.getPersons()) nameSpinnerAdapter.add(person.name);
        } catch (ExecutionException | InterruptedException e) {
            // TODO better exception handling
            e.printStackTrace();
        }
    }

    private void prefillNameViewIfFromFilteredTransactionList() {
        // Check if we come from a TransactionListFragment that was filtered by person
        // If this is the case AND we want to create a new transaction prefill the name spinner with
        // the name by which the TransactionListFragment was filtered
        NavBackStackEntry previous = nav.getPreviousBackStackEntry();
        int previousDestId = 0;
        if (previous != null)
            previousDestId = previous.getDestination().getId();
        if (previousDestId == R.id.transactionListFragment
                || previousDestId == R.id.itemTransactionListFragment) {
            Person filterPerson = personFilterViewModel.getFilterPerson();
            if (filterPerson != null && viewModel.getIdTransaction() == -1) { // TransactionList was filtered by Person and we are creating a new Transaction
                spinnerNameView.setSelection(nameSpinnerAdapter.getPosition(filterPerson.name));
                viewModel.setSelectedName(filterPerson.name);
            }
        }
    }

    private void fillViewsNewTransaction() {
        toolbar.setTitle(R.string.title_fragment_edit_transaction_add);
        viewModel.setTimestamp(new Date());
        editDateView.setText(Utilities.formatDate(viewModel.getTimestamp(),
                getString(R.string.date_format)));
        // as amount is 0.00, saving will be disabled initially
        toolbar.getMenu().findItem(R.id.miSaveTransaction).setEnabled(false);
    }

    private void fillViewsEditTransaction() {
        toolbar.setTitle(R.string.title_fragment_edit_transaction);

        TransactionWithPerson txn = null;
        try {
            txn = viewModel.getTransaction(viewModel.getIdTransaction());
        } catch(ExecutionException|InterruptedException e) {
            String errorMessage = getResources().getString(R.string.error_message_database_access, e.getLocalizedMessage());
            Toast.makeText(getContext(),  errorMessage, Toast.LENGTH_LONG).show();
            nav.navigateUp();
        }
        spinnerNameView.setSelection(nameSpinnerAdapter.getPosition(txn.person.name));
        gaveRadio.setChecked(txn.transaction.amount>0); // per default received is set (see layout xml)
        // IMPORTANT: set switchIsMonetaryView _before_ setting amount, because on setting amount the
        // AmountTextWatcher::afterTextChanged is called, and within this method isMonetary is needed to apply correct formatting!
        switchIsMonetaryView.setChecked(txn.transaction.isMonetary);
        editAmountView.setText(txn.transaction.getFormattedAmount(false));
        editDescView.setText(txn.transaction.description);
        viewModel.setTimestamp(txn.transaction.timestamp);
        editDateView.setText(Utilities.formatDate(viewModel.getTimestamp(),
                getString(R.string.date_format)));
    }

    // ---------------------------
    // Toolbar Menu event handling
    // ---------------------------

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if(id==R.id.miSaveTransaction) {
            onSaveTransactionAction();
            return true;
        }
        return true;
    }

    public void onSaveTransactionAction() {

            // at least name and amount have to be filled
            if (TextUtils.isEmpty(viewModel.getSelectedName()) || TextUtils.isEmpty(editAmountView.getText())) {
                Toast.makeText(requireContext(), R.string.add_transaction_incomplete_data, Toast.LENGTH_SHORT).show();
            } else {
                //evaluate received-gave-radios
                int factor = -1;
                if (gaveRadio.isChecked()) factor = 1;

                boolean isMonetary = switchIsMonetaryView.isChecked();

                // parse amount
                // user is expected to enter something like "10.05"(€/$/...) and we want to store 1005 (format is enforced by AmountTextWatcher)
                if (isMonetary) factor *= 100;
                int amount;
                try {
                    amount = (int) (factor * Utilities.parseAmount(editAmountView.getText().toString()));
                } catch (ParseException e) {
                    Toast.makeText(requireActivity(), R.string.add_transaction_wrong_amount_format, Toast.LENGTH_SHORT).show();
                    return;
                }

                // get person id from selected person name
                int idPerson = -1;
                try {
                    idPerson = viewModel.getSelectedPersonId();
                } catch (ExecutionException | InterruptedException e) {
                    String errorMessage = getResources().getString(R.string.error_message_database_access, e.getLocalizedMessage());
                    Toast.makeText(requireContext(), errorMessage, Toast.LENGTH_LONG).show();
                }

                // build transaction
                Transaction transaction = new Transaction(idPerson,
                        amount,
                        isMonetary,
                        editDescView.getText().toString(),
                        viewModel.getTimestamp());

                // update database
                if(viewModel.isNewTransaction()) viewModel.insert(transaction);
                else if (!viewModel.isNewTransaction()) {
                    transaction.idTransaction = viewModel.getIdTransaction();
                    viewModel.update(transaction);
                }

                nav.navigateUp();
            }
    }

    // ---------------------------
    // Date and TimePicker dialogs
    // ---------------------------

    public void showDatePickerDialog() {
        DialogFragment newFragment = new DatePickerFragment();
        newFragment.show(getParentFragmentManager(), "addTransactionDatePicker");
    }

    public static class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // Use the current date as the default date in the picker
            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            // Create a new instance of DatePickerDialog and return it
            return new DatePickerDialog(getActivity(), this, year, month, day);
        }

        public void onDateSet(DatePicker view, int year, int month, int day) {
            EditTransactionFragment fragment = (EditTransactionFragment) getParentFragment();
            final Calendar c = Calendar.getInstance();
            c.set(year, month, day);
            Date d = new Date(c.getTimeInMillis());
            assert fragment != null;
            fragment.viewModel.setTimestamp(d);
            fragment.editDateView.setText(Utilities.formatDate(d, getString(R.string.date_format)));
        }
    }

    // ------------------------------------------------------
    // Name Spinner event handling / interface implementation
    // ------------------------------------------------------

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        viewModel.setSelectedName(parent.getItemAtPosition(pos).toString());
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    // ---------------------------------------
    // Enforce correct input in editAmountView
    // ---------------------------------------

    class AmountTextWatcher implements TextWatcher {

        String formattedAmount = "";
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            String str = s.toString();
            // nothing to do on empty strings
            if (str.isEmpty()) return;
            // prevent us from looping infinitely
            if (str.equals(formattedAmount)) return;
            formattedAmount = formatArbitraryDecimalInput(str);
            editAmountView.setText(formattedAmount);
            // prevent cursor to jump to front
            editAmountView.setSelection(editAmountView.length());
        }
    }

    private String formatArbitraryDecimalInput(String input) {
        // examples (monetary): 1 --> 0,01; 0,012 --> 0,12; 0,123 --> 1,23; 1,234 --> 12,34; 12,345 --> 123,45; 123,4 --> 12,34

        String formattedAmount;
        // remove all decimal separators (this the final result for non-monetaries, where only integers are allowed)
        formattedAmount = input.replaceAll("[.,]", "");

        // check if input is short enough to be parsed as integer later
        if(formattedAmount.length()>9) { // we might be above 2^32=4.294.967.296 and later want to make an int of this String
            formattedAmount = formattedAmount.substring(0, formattedAmount.length() - 1); // so we simply remove the last digit
            Toast.makeText(requireContext(), R.string.edit_transaction_snackbar_max_amount, Toast.LENGTH_SHORT).show();
        }

        if (switchIsMonetaryView.isChecked()) {
            // add decSep two digits from the right, while adding leading zeros if needed
            // this is accomplished by removing decSep --> converting to int --> dividing by 100 --> converting to local String

            formattedAmount = Transaction.formatMonetaryAmount(Integer.parseInt(formattedAmount), Locale.getDefault());
        } else {
            // remove leading 0s
            formattedAmount = formattedAmount.replaceFirst("^0+","");
        }
        return formattedAmount;
    }

    //-------------------------------
    // Toggle isMonetary-Switch-Label
    //-------------------------------

    public void onSwitchIsMonetaryChanged(View v, boolean checked) {
        if (checked) {
            switchIsMonetaryView.setText(R.string.switch_monetary_label_money);
        } else {
            switchIsMonetaryView.setText(R.string.switch_monetary_label_item);
        }
        String s = editAmountView.getText().toString();
        if(!s.isEmpty()) {
            // apply proper formatting for newly chosen amount type
            editAmountView.setText(formatArbitraryDecimalInput(s));
        }
    }
}