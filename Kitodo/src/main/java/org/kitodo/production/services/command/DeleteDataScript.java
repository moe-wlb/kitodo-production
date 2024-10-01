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

package org.kitodo.production.services.command;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.kitodo.api.Metadata;
import org.kitodo.api.MetadataEntry;
import org.kitodo.api.dataformat.Workpiece;
import org.kitodo.data.database.beans.Process;
import org.kitodo.exceptions.KitodoScriptExecutionException;
import org.kitodo.production.helper.metadata.legacytypeimplementations.LegacyMetsModsDigitalDocumentHelper;

public class DeleteDataScript extends EditDataScript {

    /**
     * Executes the given script on the given file for the given process.
     * @param metadataFile the file to edit
     * @param process the related process
     * @param metadataScript the script to execute
     */
    @Override
    public void executeScript(LegacyMetsModsDigitalDocumentHelper metadataFile, Process process,
                               MetadataScript metadataScript) throws KitodoScriptExecutionException {
        Workpiece workpiece = metadataFile.getWorkpiece();

        Collection<Metadata> metadataCollection = getMetadataCollection(metadataScript, workpiece);

        generateValueForMetadataScript(metadataScript, metadataCollection, process, metadataFile);
        List<Metadata> metadataCollectionCopy = new ArrayList<>(metadataCollection);
        for (Metadata metadata : metadataCollectionCopy) {
            if (metadata.getKey().equals(metadataScript.getMetadataKey())) {
                if (metadataScript.getValues().isEmpty()) {
                    metadataCollection.remove(metadata);
                } else {
                    for (String value : metadataScript.getValues()) {
                        if (value.equals(((MetadataEntry) metadata).getValue())) {
                            metadataCollection.remove(metadata);
                        }
                    }
                }
            }
        }
        saveChanges(workpiece, process);

    }


}
