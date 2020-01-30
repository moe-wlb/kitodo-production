/*
 * (c) Kitodo. Key to digital objects e. V. <contact@kitodo.org>
 *
 * This file is part of the Kitodo project.
 *
 * It is licensed under GNU General Public License version 3 or later.
 *
 * For the full copyright and license information, please read the
 * GPL3-License.txt file that was distributed with this source code.
 */

package org.kitodo.production.services.schema;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;

import org.kitodo.api.MdSec;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataformat.IncludedStructuralElement;
import org.kitodo.api.dataformat.MediaUnit;
import org.kitodo.api.dataformat.MediaVariant;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.api.dataformat.mets.LinkedMetsResource;
import org.kitodo.data.database.beans.Folder;
import org.kitodo.data.database.beans.Process;
import org.kitodo.data.database.enums.LinkingMode;
import org.kitodo.data.database.exceptions.DAOException;
import org.kitodo.export.ExportMets;
import org.kitodo.production.helper.VariableReplacer;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyInnerPhysicalDocStructHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetsModsDigitalDocumentHelper;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyPrefsHelper;
import org.kitodo.production.metadata.MetadataEditor;
import org.kitodo.production.model.Subfolder;
import org.kitodo.production.services.ServiceManager;

/**
 * Service for schema manipulations.
 */
public class SchemaService {

    /**
     * Temporal method for separate file conversion from ExportMets class
     * (method writeMetsFile).
     *
     * @param workpiece
     *            class inside method is used
     * @param exportMets
     *            MetsModsImportExport object
     * @param prefs
     *            preferences - Prefs object
     * @param process
     *            object
     */
    public <T extends ExportMets> void tempConvert(Workpiece workpiece, T exportMets, LegacyPrefsHelper prefs,
            Process process) throws IOException, DAOException, URISyntaxException {
        /*
         * wenn Filegroups definiert wurden, werden diese jetzt in die
         * Metsstruktur übernommen
         */
        // Replace all paths with the given VariableReplacer, also the file
        // group paths!
        VariableReplacer vp = new VariableReplacer(
                new LegacyMetsModsDigitalDocumentHelper(prefs.getRuleset(), workpiece), prefs,
                process, null);

        addVirtualFileGroupsToMetsMods(workpiece, process);
        replaceFLocatForExport(workpiece, process);

        // Replace rights and digiprov entries.
        set(workpiece, MdSec.RIGHTS_MD, "owner", vp.replace(process.getProject().getMetsRightsOwner()));
        set(workpiece, MdSec.RIGHTS_MD, "ownerLogo", vp.replace(process.getProject().getMetsRightsOwnerLogo()));
        set(workpiece, MdSec.RIGHTS_MD, "ownerSiteURL", vp.replace(process.getProject().getMetsRightsOwnerSite()));
        set(workpiece, MdSec.RIGHTS_MD, "ownerContact", vp.replace(process.getProject().getMetsRightsOwnerMail()));
        set(workpiece, MdSec.DIGIPROV_MD, "presentation",
            vp.replace(process.getProject().getMetsDigiprovPresentation()));
        set(workpiece, MdSec.DIGIPROV_MD, "reference", vp.replace(process.getProject().getMetsDigiprovReference()));

        set(workpiece, MdSec.TECH_MD, "purlUrl", vp.replace(process.getProject().getMetsPurl()));
        set(workpiece, MdSec.TECH_MD, "contentIDs", vp.replace(process.getProject().getMetsContentIDs()));

        convertChildrenLinksForExport(workpiece, workpiece.getRootElement(), prefs);
        if (Objects.nonNull(process.getParent())) {
            addParentLinkForExport(prefs, workpiece, process.getParent());
        }

        assignViewsFromChildrenRecursive(workpiece.getRootElement());
    }

    /**
     * At all levels, assigns the views of the children to the included
     * structural elements.
     *
     * @param includedStructuralElement
     *            included structural element on which the recursion is
     *            performed
     */
    private void assignViewsFromChildrenRecursive(IncludedStructuralElement includedStructuralElement) {
        List<IncludedStructuralElement> children = includedStructuralElement.getChildren();
        if (!children.isEmpty()) {
            for (IncludedStructuralElement child : children) {
                assignViewsFromChildrenRecursive(child);
            }
            MetadataEditor.assignViewsFromChildren(includedStructuralElement);
        }
    }

    private void set(Workpiece workpiece, MdSec domain, String key, String value) {
        MetadataEntry entry = new MetadataEntry();
        entry.setKey(key);
        entry.setDomain(domain);
        entry.setValue(value);
        workpiece.getRootElement().getMetadata().add(entry);
    }

    private void addVirtualFileGroupsToMetsMods(Workpiece workpiece, Process process) {
        List<Folder> folders = process.getProject().getFolders();
        Subfolder useLocalSubfolder = getUseLocalSubfolder(process);
        for (MediaUnit mediaUnit : workpiece.getMediaUnits()) {
            String canonical = useLocalSubfolder
                    .getCanonical(mediaUnit.getMediaFiles().get(LegacyInnerPhysicalDocStructHelper.LOCAL));
            if (Objects.nonNull(canonical)) {
                removeFLocatsForUnwantedUses(process, folders, mediaUnit, canonical);
                addMissingUses(process, folders, mediaUnit, canonical);
            }
        }
    }

    private void replaceFLocatForExport(Workpiece workpiece, Process process) throws URISyntaxException {
        List<Folder> folders = process.getProject().getFolders();
        VariableReplacer variableReplacer = new VariableReplacer(null, null, process, null);
        for (MediaUnit mediaUnit : workpiece.getMediaUnits()) {
            for (Entry<MediaVariant, URI> mediaFileForMediaVariant : mediaUnit.getMediaFiles().entrySet()) {
                for (Folder folder : folders) {
                    if (folder.getFileGroup().equals(mediaFileForMediaVariant.getKey().getUse())) {
                        int lastSeparator = mediaFileForMediaVariant.getValue().toString().lastIndexOf(File.separator);
                        String lastSegment = mediaFileForMediaVariant.getValue().toString()
                                .substring(lastSeparator + 1);
                        mediaFileForMediaVariant
                                .setValue(new URI(variableReplacer.replace(folder.getUrlStructure() + lastSegment)));
                    }
                }
            }
        }
    }

    /**
     * If the media unit contains a media variant that is unknown, has linking
     * mode NO or has linking mode EXISTING but the file does not exist, remove
     * it.
     */
    private void removeFLocatsForUnwantedUses(Process process, List<Folder> folders,
            MediaUnit mediaUnit,
            String canonical) {
        for (Entry<MediaVariant, URI> mediaFileForMediaVariant : mediaUnit.getMediaFiles().entrySet()) {
            String use = mediaFileForMediaVariant.getKey().getUse();
            Optional<Folder> optionalFolderForUse = folders.parallelStream()
                    .filter(folder -> use.equals(folder.getFileGroup())).findAny();
            if (!optionalFolderForUse.isPresent()
                    || optionalFolderForUse.get().getLinkingMode().equals(LinkingMode.NO)
                    || (optionalFolderForUse.get().getLinkingMode().equals(LinkingMode.EXISTING)
                            && new Subfolder(process, optionalFolderForUse.get()).getURIIfExists(canonical)
                                    .isPresent())) {
                mediaUnit.getMediaFiles().remove(mediaFileForMediaVariant.getKey());
            }
        }
    }

    /**
     * If the media unit is missing a variant that has linking mode ALL or has
     * linking mode EXISTING and the file does exist, add it.
     */
    private void addMissingUses(Process process, List<Folder> folders, MediaUnit mediaUnit,
            String canonical) {
        for (Folder folder : folders) {
            Subfolder useFolder = new Subfolder(process, folder);
            if (mediaUnit.getMediaFiles().entrySet().parallelStream().map(Entry::getKey).map(MediaVariant::getUse)
                    .noneMatch(use -> use.equals(folder.getFileGroup())) && (folder.getLinkingMode().equals(LinkingMode.ALL)
                        || (folder.getLinkingMode().equals(LinkingMode.EXISTING) && useFolder.getURIIfExists(canonical).isPresent()))) {
                addUse(useFolder, canonical, mediaUnit);
            }
        }
    }

    /**
     * Returns the USE="LOCAL" subfolder.
     *
     * @param process
     *            process whose USE="LOCAL" subfolder shall be returned
     * @return the subfolder with USE="LOCAL"
     */
    private Subfolder getUseLocalSubfolder(Process process) {
        Folder useLocalFolder = process.getProject().getGeneratorSource();
        if (Objects.isNull(useLocalFolder)) {
            Optional<Folder> optionalUseLocalFolderByName = process.getProject().getFolders().parallelStream()
                    .filter(folder -> folder.getFileGroup().equals("LOCAL")).findAny();
            if (optionalUseLocalFolderByName.isPresent()) {
                useLocalFolder = optionalUseLocalFolderByName.get();
            }
        }
        return new Subfolder(process, useLocalFolder);
    }

    /**
     * Adds a use to a media unit.
     *
     * @param useFolder
     *            use folder for the use
     * @param canonical
     *            the canonical part of the file name of the media file
     * @param mediaUnit
     *            media unit to add to
     */
    private void addUse(Subfolder useFolder, String canonical, MediaUnit mediaUnit) {
        MediaVariant mediaVariant = new MediaVariant();
        mediaVariant.setUse(useFolder.getFolder().getFileGroup());
        mediaVariant.setMimeType(useFolder.getFolder().getMimeType());
        URI mediaFile = useFolder.getUri(canonical);
        mediaUnit.getMediaFiles().put(mediaVariant, mediaFile);
    }

    private void convertChildrenLinksForExport(Workpiece workpiece, IncludedStructuralElement structure,
                                               LegacyPrefsHelper prefs) throws DAOException, IOException {

        LinkedMetsResource link = structure.getLink();
        if (Objects.nonNull(link)) {
            int linkedProcessId = ServiceManager.getProcessService().processIdFromUri(link.getUri());
            Process process = ServiceManager.getProcessService().getById(linkedProcessId);
            setLinkForExport(structure, process, prefs, workpiece);
        }
        for (IncludedStructuralElement child : structure.getChildren()) {
            convertChildrenLinksForExport(workpiece, child, prefs);
        }
    }

    private void addParentLinkForExport(LegacyPrefsHelper prefs, Workpiece workpiece, Process parent)
            throws IOException {

        IncludedStructuralElement linkHolder = new IncludedStructuralElement();
        linkHolder.setLink(new LinkedMetsResource());
        setLinkForExport(linkHolder, parent, prefs, workpiece);
        linkHolder.getChildren().add(workpiece.getRootElement());
        workpiece.setRootElement(linkHolder);
    }

    private void setLinkForExport(IncludedStructuralElement structure, Process process, LegacyPrefsHelper prefs,
            Workpiece workpiece) throws IOException {

        LinkedMetsResource link = structure.getLink();
        link.setLoctype("URL");
        String uriWithVariables = process.getProject().getMetsPointerPath();
        VariableReplacer variableReplacer = new VariableReplacer(
                new LegacyMetsModsDigitalDocumentHelper(prefs.getRuleset(), workpiece), prefs, process, null);
        String linkUri = variableReplacer.replace(uriWithVariables);
        link.setUri(URI.create(linkUri));
        structure.setType(ServiceManager.getProcessService().getBaseType(process));
    }
}
