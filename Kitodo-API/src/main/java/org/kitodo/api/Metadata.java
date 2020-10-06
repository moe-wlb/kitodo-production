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

package org.kitodo.api;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Metadata {
    /**
     * In which conceptual area in the METS file this metadata entry is stored.
     */
    protected MdSec domain;

    /**
     * The key of the metadata.
     */
    protected String key;

    /**
     * Returns the domain of the metadata.
     *
     * @return the location of the metadata entry
     */
    public MdSec getDomain() {
        return domain;
    }

    /**
     * Get the key o the metadata.
     *
     * @return The key of the metadata.
     */
    public String getKey() {
        return key;
    }

    /**
     * Sets the domain of the metadata.
     *
     * @param domain
     *            location to set for the metadata entry
     */
    public void setDomain(MdSec domain) {
        this.domain = domain;
    }

    /**
     * Set the key of the metadata.
     *
     * @param key
     *            The key value of the metadata.
     */
    public void setKey(String key) {
        this.key = key;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Metadata metadata = (Metadata) o;
        return domain == metadata.domain
                && Objects.equals(key, metadata.key);
    }

    /**
     * Map Collection of Metadata objects to a Map of Metadata and String objects.
     * @param metadataCollection Collection of Metadata objects
     * @return Map of Metadata and String objects as java.util.Map
     */
    public static Map<Metadata, String> mapToKey(Collection<Metadata> metadataCollection) {
        if (Objects.isNull(metadataCollection)) {
            return Collections.emptyMap();
        }
        return metadataCollection.parallelStream()
                .collect(Collectors.toMap(Function.identity(), Metadata::getKey, (duplicateOne, duplicateTwo) -> duplicateOne));
    }
}
