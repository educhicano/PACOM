package org.proteored.pacom.analysis.conf;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FilenameUtils;
import org.apache.log4j.Logger;
import org.proteored.miapeapi.cv.ControlVocabularyManager;
import org.proteored.miapeapi.exceptions.IllegalMiapeArgumentException;
import org.proteored.miapeapi.exceptions.MiapeDatabaseException;
import org.proteored.miapeapi.exceptions.MiapeSecurityException;
import org.proteored.miapeapi.experiment.model.Replicate;
import org.proteored.miapeapi.experiment.model.filters.Filter;
import org.proteored.miapeapi.interfaces.Adapter;
import org.proteored.miapeapi.interfaces.ms.MiapeMSDocument;
import org.proteored.miapeapi.interfaces.msi.IdentifiedProtein;
import org.proteored.miapeapi.interfaces.msi.IdentifiedProteinSet;
import org.proteored.miapeapi.interfaces.msi.MiapeMSIDocument;
import org.proteored.miapeapi.xml.ms.MIAPEMSXmlFile;
import org.proteored.miapeapi.xml.ms.MiapeMSXmlFactory;
import org.proteored.miapeapi.xml.msi.IdentifiedProteinImpl;
import org.proteored.miapeapi.xml.msi.MIAPEMSIXmlFile;
import org.proteored.miapeapi.xml.msi.MiapeMSIXmlFactory;
import org.proteored.pacom.analysis.conf.jaxb.CPMS;
import org.proteored.pacom.analysis.conf.jaxb.CPMSI;
import org.proteored.pacom.analysis.conf.jaxb.CPReplicate;
import org.proteored.pacom.analysis.util.FileManager;
import org.proteored.pacom.gui.tasks.OntologyLoaderTask;

import edu.scripps.yates.annotations.uniprot.UniprotProteinLocalRetriever;
import edu.scripps.yates.annotations.uniprot.UniprotRetriever;
import edu.scripps.yates.annotations.uniprot.xml.Entry;
import edu.scripps.yates.annotations.uniprot.xml.EvidencedStringType;
import edu.scripps.yates.annotations.uniprot.xml.ProteinType;
import edu.scripps.yates.annotations.uniprot.xml.ProteinType.AlternativeName;
import edu.scripps.yates.annotations.uniprot.xml.ProteinType.RecommendedName;
import edu.scripps.yates.annotations.uniprot.xml.ProteinType.SubmittedName;
import edu.scripps.yates.utilities.fasta.FastaParser;

public class ReplicateAdapter implements Adapter<Replicate> {
	private final CPReplicate xmlRep;
	private final ControlVocabularyManager cvManager;
	private final String experimentName;
	private static final Logger log = Logger.getLogger("log4j.logger.org.proteored");
	private final boolean curated;
	private final Integer minPeptideLength;
	private final List<Filter> filters;
	private final boolean processInParallel;

	public ReplicateAdapter(CPReplicate xmlRep, String experimentName, boolean curated) {
		this(xmlRep, experimentName, curated, null, null, false);
	}

	public ReplicateAdapter(CPReplicate xmlRep, String experimentName, boolean curated, boolean processInParallel) {
		this(xmlRep, experimentName, curated, null, null, processInParallel);
	}

	public ReplicateAdapter(CPReplicate xmlRep, String experimentName, boolean curated, Integer minPeptideLength,
			List<Filter> filters, boolean processInParallel) {
		this.xmlRep = xmlRep;
		cvManager = OntologyLoaderTask.getCvManager();
		this.experimentName = experimentName;
		this.curated = curated;
		this.minPeptideLength = minPeptideLength;
		this.filters = filters;
		this.processInParallel = processInParallel;
	}

	@Override
	public Replicate adapt() {
		List<MiapeMSDocument> miapeMSs = new ArrayList<MiapeMSDocument>();
		List<MiapeMSIDocument> miapeMSIs = new ArrayList<MiapeMSIDocument>();
		log.info("Adapting replicate");
		if (xmlRep.getCPMSIList() != null) {
			for (CPMSI cpMsi : xmlRep.getCPMSIList().getCPMSI()) {
				if (cpMsi.isManuallyCreated() != null && cpMsi.isManuallyCreated()) {
					if (cpMsi.getName() != null && !"".equals(cpMsi.getName())) {
						log.info("Reading Manually created MIAPE MSI file: " + cpMsi.getName());
						MiapeMSIDocument miapeMSI = getMIAPEMSIFromManuallyCreatedFile(cpMsi.getName());
						try {
							// Para que se pueda interrumpir el proceso
							Thread.sleep(1L);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						if (miapeMSI != null)
							miapeMSIs.add(miapeMSI);
						else
							log.warn("Error reading manually created MIAPE MSI file: " + cpMsi.getName());
					}
				} else if (cpMsi.isLocal() != null && cpMsi.isLocal() && !"".equals(cpMsi.getName())) {
					log.info("Reading locally created MIAPE MSI file: " + cpMsi.getName());
					MiapeMSIDocument miapeMSI = getMIAPEMSIFromFile(cpMsi);
					try {
						// Para que se pueda interrumpir el proceso
						Thread.sleep(1L);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					if (miapeMSI != null)
						miapeMSIs.add(miapeMSI);
					else
						log.warn("Error reading manually created MIAPE MSI file: " + cpMsi.getName());

				} else {
					if (cpMsi.getName() == null) {
						cpMsi.setName(FilenameUtils
								.getBaseName(FileManager.getMiapeMSILocalFileName(cpMsi.getId(), cpMsi.getName())));
					}
					if (cpMsi.getName() != null && !"".equals(cpMsi.getName())) {
						log.info("Reading MIAPE MSI file: " + cpMsi.getName());
						MiapeMSIDocument miapeMSI = getMIAPEMSIFromFile(cpMsi);
						try {
							// Para que se pueda interrumpir el proceso
							Thread.sleep(1L);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						if (miapeMSI != null)
							miapeMSIs.add(miapeMSI);
						else
							log.warn("Error reading MIAPE MSI file: " + cpMsi.getName());
					}
				}
			}
		}
		if (xmlRep.getCPMSList() != null) {
			for (CPMS cpMs : xmlRep.getCPMSList().getCPMS()) {
				if (cpMs != null) {
					if (cpMs.isLocal() != null && cpMs.isLocal()) {
						// cpMs.setName(FileManager.getMiapeMSLocalFileName(cpMs.getId()));
						log.info("Reading locally created MIAPE MS file: " + cpMs.getName());
						MiapeMSDocument miapeMS = getMIAPEMSFromLocallyCreatedFile(cpMs.getId(),
								cpMs.getLocalProjectName(), cpMs.getName());
						if (miapeMS != null)
							miapeMSs.add(miapeMS);
						else
							log.warn("Error reading MIAPE MS file: " + cpMs.getName());

					} else {
						cpMs.setName(FilenameUtils
								.getBaseName(FileManager.getMiapeMSLocalFileName(cpMs.getId(), cpMs.getName())));
						log.info("Reading MIAPE MS file: " + cpMs.getName());
						MiapeMSDocument miapeMS = getMIAPEMSFromFile(cpMs);
						if (miapeMS != null)
							miapeMSs.add(miapeMS);
						else
							log.warn("Error reading MIAPE MS file: " + cpMs.getName());
					}
				}
			}
		}

		log.info("Creating replicate: " + xmlRep.getName());
		Replicate rep = new Replicate(xmlRep.getName(), experimentName, miapeMSs, miapeMSIs, filters, minPeptideLength,
				OntologyLoaderTask.getCvManager(), processInParallel);
		log.info("Replicate: " + xmlRep.getName() + " created.");
		return rep;

	}

	private MiapeMSDocument getMIAPEMSFromFile(CPMS cpMs) {
		File file = new File(FileManager.getMiapeMSXMLFileLocalPath(cpMs.getId(), experimentName, cpMs.getName()));
		if (!file.exists())
			return null;
		MiapeMSDocument ret;
		MIAPEMSXmlFile msFile = new MIAPEMSXmlFile(file);
		msFile.setCvUtil(cvManager);
		try {
			ret = msFile.toDocument();

			return ret;
		} catch (MiapeDatabaseException e) {
			log.warn(e.getMessage());
			e.printStackTrace();
		} catch (MiapeSecurityException e) {
			log.warn(e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	private MiapeMSIDocument getMIAPEMSIFromFile(CPMSI cpMsi) {

		File file = null;
		if (curated) {
			file = new File(FileManager.getMiapeMSICuratedXMLFilePathFromMiapeInformation(cpMsi.getLocalProjectName(),
					cpMsi.getId(), cpMsi.getName()));
		} else {
			file = new File(FileManager.getMiapeMSIXMLFileLocalPathFromMiapeInformation(cpMsi));
		}
		if (!file.exists()) {
			throw new IllegalMiapeArgumentException("Error loading MIAPE MSI file: " + file.getName()
					+ " not found at: " + FileManager.getMiapeDataPath());
		}
		MiapeMSIDocument ret;

		MIAPEMSIXmlFile msiFile = new MIAPEMSIXmlFile(file);

		try {
			ret = MiapeMSIXmlFactory.getFactory(processInParallel).toDocument(msiFile, cvManager, null, null, null);
			return ret;
		} catch (MiapeDatabaseException e) {
			log.warn(e.getMessage());
			e.printStackTrace();
		} catch (MiapeSecurityException e) {
			log.warn(e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			log.warn(e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	private MiapeMSIDocument getMIAPEMSIFromManuallyCreatedFile(String name) {

		File file = FileManager.getManualIdSetFile(name);
		if (file != null && !file.exists())
			throw new IllegalMiapeArgumentException("Error loading manually created MIAPE MSI file: " + file.getName()
					+ " not found at: " + FileManager.getManualIdSetPath());
		MiapeMSIDocument ret;

		MIAPEMSIXmlFile msiFile = new MIAPEMSIXmlFile(file);

		try {
			ret = MiapeMSIXmlFactory.getFactory(processInParallel).toDocument(msiFile, cvManager, null, null, null);
			addProteinDescriptionFromUniprot(ret);
			return ret;
		} catch (MiapeDatabaseException e) {
			log.warn(e.getMessage());
			e.printStackTrace();
		} catch (MiapeSecurityException e) {
			log.warn(e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			log.warn(e.getMessage());
			e.printStackTrace();
		}
		return null;
	}

	private void addProteinDescriptionFromUniprot(MiapeMSIDocument ret) {
		// complete the information of the proteins with the Uniprot
		// information
		Set<String> accessionsToLookUp = new HashSet<String>();
		for (IdentifiedProteinSet proteinSet : ret.getIdentifiedProteinSets()) {
			final HashMap<String, IdentifiedProtein> identifiedProteins = proteinSet.getIdentifiedProteins();
			for (String proteinAcc : identifiedProteins.keySet()) {
				final IdentifiedProtein protein = identifiedProteins.get(proteinAcc);
				if (protein instanceof IdentifiedProteinImpl) {
					if (protein.getDescription() == null || "".equals(protein.getDescription())) {
						if (FastaParser.isUniProtACC(protein.getAccession())
								&& !protein.getAccession().contains("Reverse")) {
							accessionsToLookUp.add(protein.getAccession());

						}
					}

				}
			}
		}
		if (!accessionsToLookUp.isEmpty()) {
			log.info("Trying to recover protein descriptions for " + accessionsToLookUp.size() + " proteins");
			File uniprotReleasesFolder = FileManager.getUniprotFolder();
			UniprotRetriever upr = new UniprotProteinLocalRetriever(uniprotReleasesFolder, true);
			final Map<String, Entry> annotatedProteins = upr.getAnnotatedProteins(null, accessionsToLookUp);
			for (IdentifiedProteinSet proteinSet : ret.getIdentifiedProteinSets()) {
				for (String proteinAcc : proteinSet.getIdentifiedProteins().keySet()) {
					if (annotatedProteins.containsKey(proteinAcc)) {
						String description = null;
						final Entry uniprotProtein = annotatedProteins.get(proteinAcc);
						if (uniprotProtein.getAccession() != null) {
							final List<String> descriptions = getDescriptions(uniprotProtein);
							if (descriptions != null && !descriptions.isEmpty()) {
								description = descriptions.get(0);
							}
						}
						if (description != null) {
							final IdentifiedProtein protein = proteinSet.getIdentifiedProteins().get(proteinAcc);
							if (protein instanceof IdentifiedProteinImpl) {
								((IdentifiedProteinImpl) protein).setDescription(description);
							}
						}
					}
				}
			}
		}

	}

	private List<String> getDescriptions(Entry entry) {
		final ProteinType protein = entry.getProtein();
		List<String> ret = new ArrayList<String>();
		if (entry.getProtein() != null) {
			final RecommendedName recommendedName = protein.getRecommendedName();
			if (recommendedName != null) {
				StringBuilder sb = new StringBuilder();
				if (recommendedName.getFullName() != null && recommendedName.getFullName().getValue() != null) {
					sb.append(recommendedName.getFullName().getValue().trim());
				}
				final List<EvidencedStringType> shortNames = recommendedName.getShortName();
				if (shortNames != null && shortNames.isEmpty()) {
					for (int i = 0; i < shortNames.size(); i++) {
						EvidencedStringType shortName = shortNames.get(i);
						sb.append(" (" + shortName.getValue().trim() + ")");
					}
				}
				final List<EvidencedStringType> ecNumbers = recommendedName.getEcNumber();
				if (ecNumbers != null && ecNumbers.isEmpty()) {
					for (EvidencedStringType ecNumber : ecNumbers) {
						sb.append(" (" + ecNumber.getValue().trim() + ")");
					}
				}
				ret.add(sb.toString());
			}
			final List<AlternativeName> alternativeNames = protein.getAlternativeName();
			if (alternativeNames != null && !alternativeNames.isEmpty()) {
				for (AlternativeName alternativeName2 : alternativeNames) {
					StringBuilder sb = new StringBuilder();
					sb.append(alternativeName2.getFullName().getValue().trim());
					final List<EvidencedStringType> shortNames = alternativeName2.getShortName();
					if (shortNames != null && shortNames.isEmpty()) {
						for (int i = 0; i < shortNames.size(); i++) {
							EvidencedStringType shortName = shortNames.get(i);
							sb.append(" (" + shortName.getValue().trim() + ")");
						}
					}
					final List<EvidencedStringType> ecNumbers = alternativeName2.getEcNumber();
					if (ecNumbers != null && ecNumbers.isEmpty()) {
						for (EvidencedStringType ecNumber : ecNumbers) {
							sb.append(" (" + ecNumber.getValue().trim() + ")");
						}
					}
					ret.add(sb.toString());
				}
			}

			final List<SubmittedName> submittedNames = protein.getSubmittedName();
			if (submittedNames != null && !submittedNames.isEmpty()) {
				for (SubmittedName submittedName2 : submittedNames) {
					StringBuilder sb = new StringBuilder();
					sb.append(submittedName2.getFullName().getValue().trim());
					final List<EvidencedStringType> ecNumbers = submittedName2.getEcNumber();
					if (ecNumbers != null && ecNumbers.isEmpty()) {
						for (EvidencedStringType ecNumber : ecNumbers) {
							sb.append(" (" + ecNumber.getValue().trim() + ")");
						}
					}
					ret.add(sb.toString());
				}

			}
		}
		return ret;

	}

	private MiapeMSDocument getMIAPEMSFromLocallyCreatedFile(int id, String projectName, String miapeName) {

		File file = new File(FileManager.getMiapeMSXMLFileLocalPath(id, projectName, miapeName));
		if (!file.exists())
			throw new IllegalMiapeArgumentException("Error loading locally created MIAPE MS file: " + file.getName()
					+ " not found at: " + FileManager.getMiapeMSXMLFileLocalPath(id, projectName, miapeName));
		MiapeMSDocument ret;

		MIAPEMSXmlFile msFile = new MIAPEMSXmlFile(file);

		try {
			ret = MiapeMSXmlFactory.getFactory().toDocument(msFile, cvManager, null, null, null);
			return ret;
		} catch (MiapeDatabaseException e) {
			log.warn(e.getMessage());
			e.printStackTrace();
		} catch (MiapeSecurityException e) {
			log.warn(e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			log.warn(e.getMessage());
			e.printStackTrace();
		}
		return null;
	}
}