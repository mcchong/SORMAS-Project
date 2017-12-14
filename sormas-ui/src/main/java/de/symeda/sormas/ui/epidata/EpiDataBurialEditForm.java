package de.symeda.sormas.ui.epidata;

import com.vaadin.ui.DateField;
import com.vaadin.ui.OptionGroup;
import com.vaadin.ui.TextField;

import de.symeda.sormas.api.epidata.EpiDataBurialDto;
import de.symeda.sormas.api.user.UserRight;
import de.symeda.sormas.ui.location.LocationEditForm;
import de.symeda.sormas.ui.utils.AbstractEditForm;
import de.symeda.sormas.ui.utils.DateComparisonValidator;
import de.symeda.sormas.ui.utils.FieldHelper;
import de.symeda.sormas.ui.utils.LayoutUtil;

@SuppressWarnings("serial")
public class EpiDataBurialEditForm extends AbstractEditForm<EpiDataBurialDto> {

	private static final String HTML_LAYOUT = 
			LayoutUtil.fluidRowLocs(EpiDataBurialDto.BURIAL_DATE_FROM, EpiDataBurialDto.BURIAL_DATE_TO) +
			LayoutUtil.fluidRowLocs(EpiDataBurialDto.BURIAL_PERSON_NAME, EpiDataBurialDto.BURIAL_RELATION) +
			LayoutUtil.fluidRowLocs(EpiDataBurialDto.BURIAL_ADDRESS) +
			LayoutUtil.fluidRowLocs(EpiDataBurialDto.BURIAL_ILL, EpiDataBurialDto.BURIAL_TOUCHING)
	;
	
	public EpiDataBurialEditForm(boolean create, UserRight editOrCreateUserRight) {
		super(EpiDataBurialDto.class, EpiDataBurialDto.I18N_PREFIX, editOrCreateUserRight);
		
		setWidth(540, Unit.PIXELS);
		
		if (create) {
			hideValidationUntilNextCommit();
		}
	}
	
	@Override
	protected void addFields() {
		DateField burialDateFrom = addField(EpiDataBurialDto.BURIAL_DATE_FROM, DateField.class);
		DateField burialDateTo = addField(EpiDataBurialDto.BURIAL_DATE_TO, DateField.class);
		burialDateFrom.addValidator(new DateComparisonValidator(burialDateFrom, burialDateTo, true, true, "The " + burialDateFrom.getCaption() + " can not be later than the " + burialDateTo.getCaption() + "."));
		burialDateTo.addValidator(new DateComparisonValidator(burialDateTo, burialDateFrom, false, true, "The " + burialDateTo.getCaption() + " can not be earlier than the " + burialDateFrom.getCaption() + "."));
		addField(EpiDataBurialDto.BURIAL_PERSON_NAME, TextField.class);
		addField(EpiDataBurialDto.BURIAL_RELATION, TextField.class);
		addField(EpiDataBurialDto.BURIAL_ILL, OptionGroup.class);
		addField(EpiDataBurialDto.BURIAL_TOUCHING, OptionGroup.class);
		addField(EpiDataBurialDto.BURIAL_ADDRESS, LocationEditForm.class).setCaption(null);

		FieldHelper.addSoftRequiredStyle(burialDateFrom, burialDateTo);
		setRequired(true,
				EpiDataBurialDto.BURIAL_ILL,
				EpiDataBurialDto.BURIAL_TOUCHING);
		
	}
	
	@Override
	protected String createHtmlLayout() {
		return HTML_LAYOUT;
	}
	
}
