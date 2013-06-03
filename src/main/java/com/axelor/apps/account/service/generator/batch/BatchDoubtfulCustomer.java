package com.axelor.apps.account.service.generator.batch;

import java.util.List;

import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.apps.account.db.Account;
import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.MoveLine;
import com.axelor.apps.account.service.debtrecovery.DoubtfulCustomerService;
import com.axelor.apps.base.db.Company;
import com.axelor.db.JPA;
import com.axelor.exception.AxelorException;
import com.axelor.exception.db.IException;
import com.axelor.exception.service.TraceBackService;

public class BatchDoubtfulCustomer extends BatchStrategy {

	private static final Logger LOG = LoggerFactory.getLogger(BatchDoubtfulCustomer.class);

	private boolean stop = false;
	
	@Inject
	public BatchDoubtfulCustomer(DoubtfulCustomerService doubtfulCustomerService) {
		
		super(doubtfulCustomerService);
	}
	
	
	@Override
	protected void start() throws IllegalArgumentException, IllegalAccessException {
		
		super.start();
		
		Company company = batch.getAccountingBatch().getCompany();
				
		try {
			
			doubtfulCustomerService.testCompanyField(company);
			
		} catch (AxelorException e) {
			
			TraceBackService.trace(new AxelorException("", e, e.getcategory()), IException.DOUBTFUL_CUSTOMER, batch.getId());
			incrementAnomaly();
			stop = true;
		}
		
		checkPoint();

	}

	
	@Override
	protected void process() {
	
		if(!stop)  {
			Company company = batch.getAccountingBatch().getCompany();
			
			Account doubtfulCustomerAccount = company.getDoubtfulCustomerAccount();
			String sixMonthDebtPassReason = company.getSixMonthDebtPassReason();
			String threeMonthDebtPassReason = company.getThreeMonthDebtPassReason();
		
			// FACTURES
			List<Move> moveList = doubtfulCustomerService.getMove(0, doubtfulCustomerAccount, company);
			LOG.debug("Nombre d'écritures de facture concernées (Créance de + 6 mois) au 411 : {} ",moveList.size());
			this.createDoubtFulCustomerMove(moveList, doubtfulCustomerAccount, sixMonthDebtPassReason);
			
			moveList = doubtfulCustomerService.getMove(1, doubtfulCustomerAccount, company);
			LOG.debug("Nombre d'écritures de facture concernées (Créance de + 3 mois) au 411 : {} ",moveList.size());
			this.createDoubtFulCustomerMove(moveList, doubtfulCustomerAccount, threeMonthDebtPassReason);
			
			// FACTURES REJETES
			List<MoveLine> moveLineList = doubtfulCustomerService.getRejectMoveLine(0, doubtfulCustomerAccount, company);
			LOG.debug("Nombre de lignes d'écriture de rejet concernées (Créance de + 6 mois) au 411 : {} ",moveLineList.size());
			this.createDoubtFulCustomerRejectMove(moveLineList, doubtfulCustomerAccount, sixMonthDebtPassReason);
			
			moveLineList = doubtfulCustomerService.getRejectMoveLine(1, doubtfulCustomerAccount, company);
			LOG.debug("Nombre de lignes d'écriture de rejet concernées (Créance de + 3 mois) au 411 : {} ",moveLineList.size());
			this.createDoubtFulCustomerRejectMove(moveLineList, doubtfulCustomerAccount, threeMonthDebtPassReason);
	
		}
		
	}
	
	
	
	/**
	 * 
	 * Procédure permettant de créer les écritures de passage en client douteux pour chaque écriture de facture
	 * @param moveLineList
	 * 			Une liste d'écritures de facture
	 * @param doubtfulCustomerAccount
	 * 			Un compte client douteux
	 * @param debtPassReason
	 * 			Un motif de passage en client douteux
	 * @throws AxelorException
	 */
	public void createDoubtFulCustomerMove(List<Move> moveList, Account doubtfulCustomerAccount, String debtPassReason)  {
		
		int i = 0;
		
		for(Move move : moveList)  {
			try {
				
				doubtfulCustomerService.createDoubtFulCustomerMove(Move.find(move.getId()), Account.find(doubtfulCustomerAccount.getId()), debtPassReason);
				updateInvoice(Move.find(move.getId()).getInvoice()); 
			
			} catch (AxelorException e) {
				
				TraceBackService.trace(new AxelorException(String.format("Facture %s", move.getInvoice().getInvoiceId()), e, e.getcategory()), IException.DOUBTFUL_CUSTOMER, batch.getId());
				incrementAnomaly();
				
			} catch (Exception e) {
				
				TraceBackService.trace(new Exception(String.format("Facture %s", move.getInvoice().getInvoiceId()), e), IException.DOUBTFUL_CUSTOMER, batch.getId());
				
				incrementAnomaly();
				
				LOG.error("Bug(Anomalie) généré(e) pour le contrat {}", move.getInvoice().getInvoiceId());
				
			} finally {
				
				if (i % 10 == 0) { JPA.clear(); }
	
			}	
		}
	}
	
	
	
	/**
	 * Procédure permettant de créer les écritures de passage en client douteux pour chaque ligne d'écriture de rejet de facture
	 * @param moveLineList
	 * 			Une liste de lignes d'écritures de rejet de facture
	 * @param doubtfulCustomerAccount
	 * 			Un compte client douteux
	 * @param debtPassReason
	 * 			Un motif de passage en client douteux
	 * @throws AxelorException
	 */
	public void createDoubtFulCustomerRejectMove(List<MoveLine> moveLineList, Account doubtfulCustomerAccount, String debtPassReason)  {
		
		int i = 0;
		
		for(MoveLine moveLine : moveLineList)  {
			
			try {
				
				doubtfulCustomerService.createDoubtFulCustomerRejectMove(MoveLine.find(moveLine.getId()), Account.find(doubtfulCustomerAccount.getId()), debtPassReason);
				updateInvoice(MoveLine.find(moveLine.getId()).getInvoiceReject()); 
				i++;
				
			} catch (AxelorException e) {
				
				TraceBackService.trace(new AxelorException(String.format("Facture %s", moveLine.getInvoiceReject().getInvoiceId()), e, e.getcategory()), IException.DOUBTFUL_CUSTOMER, batch.getId());
				incrementAnomaly();
				
			} catch (Exception e) {
				
				TraceBackService.trace(new Exception(String.format("Facture %s", moveLine.getInvoiceReject().getInvoiceId()), e), IException.DOUBTFUL_CUSTOMER, batch.getId());
				
				incrementAnomaly();
				
				LOG.error("Bug(Anomalie) généré(e) pour le contrat {}", moveLine.getInvoiceReject().getInvoiceId());
				
			} finally {
				
				if (i % 10 == 0) { JPA.clear(); }
	
			}	
		}
	}
	
	
	
	
	
	

	/**
	 * As {@code batch} entity can be detached from the session, call {@code Batch.find()} get the entity in the persistant context.
	 * Warning : {@code batch} entity have to be saved before.
	 */
	@Override
	protected void stop() {

		String comment = "Compte rendu de la détermination des créances douteuses :\n";
		comment += String.format("\t* %s Facture(s) traitée(s)\n", batch.getDone());
		comment += String.format("\t* %s anomalie(s)", batch.getAnomaly());

		super.stop();
		addComment(comment);
		
	}

}
