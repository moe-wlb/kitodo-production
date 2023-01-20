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

package org.kitodo.production.filters;

import org.kitodo.data.database.beans.User;
import org.kitodo.production.enums.FilterPart;
import org.kitodo.production.enums.FilterString;
import org.kitodo.production.forms.CurrentTaskForm;
import org.kitodo.production.forms.ProcessForm;
import org.kitodo.production.forms.UserForm;
import org.kitodo.production.services.ServiceManager;
import org.kitodo.production.services.data.FilterService;

import javax.faces.context.FacesContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class FilterMenu {

    private static final int MAX_SUGGESTIONS = 15;

    private ProcessForm processForm = null;
    private CurrentTaskForm taskForm = null;
    private UserForm userForm = null;
    private List<Suggestion> suggestions;
    private final List<ParsedFilter> parsedFilters;
    private String filterInEditMode;

    /**
     * Constructor of filter menu for processes.
     *
     * @param processForm instance of ProcessForm
     */
    public FilterMenu(ProcessForm processForm) {
        this.processForm = processForm;
        suggestions = createSuggestionsForProcessAndTaskCategory("");
        parsedFilters = new ArrayList<>();
    }

    /**
     * Constructor of filter menu for tasks.
     *
     * @param taskForm instance of CurrentTaskForm
     */
    public FilterMenu(CurrentTaskForm taskForm) {
        this.taskForm = taskForm;
        suggestions = createSuggestionsForProcessAndTaskCategory("");
        parsedFilters = new ArrayList<>();
    }

    /**
     * Constructor of filter menu for users.
     *
     * @param userForm instance of UserForm
     */
    public FilterMenu(UserForm userForm) {
        this.userForm = userForm;
        suggestions = createSuggestionsForUserCategory("");
        parsedFilters = new ArrayList<>();
    }

    /**
     * Get list of suggestions.
     *
     * @return List of suggestion objects
     */
    public List<Suggestion> getSuggestions() {
        return suggestions;
    }

    /**
     * Create suggestions based on a String passed from a {@code <p:remoteCommand/>}.
     */
    public void updateSuggestions() {
        Map<String, String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        updateSuggestions(params.get("input"));
    }

    /**
     * Create suggestions based on the given input string.
     *
     * @param input String to get suggestions for
     */
    public void updateSuggestions(String input) {
        // remove quotation marks and minus
        String strippedInput = input.replaceAll("^[\"-]+|\"$", "");

        // determine the last part of the input
        int lastColonIndex = strippedInput.lastIndexOf(":");
        if (lastColonIndex == -1) {
            // category should be suggested
            if (Objects.nonNull(processForm) || Objects.nonNull(taskForm)) {
                suggestions = createSuggestionsForProcessAndTaskCategory(input);
            } else if (Objects.nonNull(userForm)) {
                suggestions = createSuggestionsForUserCategory(input);
            }
        } else {
            // at least one colon in the input string
            String lastPart = input.substring(lastColonIndex + 1);
            Pattern patternNextCategory = Pattern.compile("(?<= \\| )\\w?$");
            Matcher matcherNextCategory = patternNextCategory.matcher(lastPart);
            if (Objects.nonNull(processForm) || Objects.nonNull(taskForm)) {
                if (matcherNextCategory.find()) {
                    // strings ends with " | "
                    // process/task category should be suggested
                    suggestions = createSuggestionsForProcessAndTaskCategory(matcherNextCategory.group());
                } else {
                    // process/task value should be suggested
                    Pattern patternPreviousCategory = Pattern.compile("\\w+:(?!.*:)");
                    Matcher matcherPreviousCategory = patternPreviousCategory.matcher(input);
                    String category = matcherPreviousCategory.find() ? matcherPreviousCategory.group() : "";
                    suggestions = createSuggestionsForProcessAndTaskValue(checkProcessFilterCategory(category), lastPart);
                }
            } else if (Objects.nonNull(userForm)) {
                if (matcherNextCategory.find()) {
                    // strings ends with " | "
                    // user category should be suggested
                    suggestions = createSuggestionsForUserCategory(matcherNextCategory.group());
                }
            }
        }
    }
    
    private List<Suggestion> createSuggestionsForProcessAndTaskCategory(String input) {
        List<Suggestion> suggestions = new ArrayList<>();
        for (FilterString filterString : FilterString.values()) {
            if (filterString.getFilterEnglish().startsWith(input.toLowerCase())
                    || filterString.getFilterGerman().startsWith(input.toLowerCase())) {
                suggestions.add(new Suggestion(input, filterString.getFilterEnglish(), FilterPart.CATEGORY));
            }
        }
        return suggestions;
    }

    private List<Suggestion> createSuggestionsForProcessAndTaskValue(FilterString category, String input) {
        List<Suggestion> suggestions = new ArrayList<>();
        if (Objects.isNull(category)) {
            return suggestions;
        }
        FilterService filterService = ServiceManager.getFilterService();
        switch (category) {
            case TASK:
            case TASKINWORK:
            case TASKLOCKED:
            case TASKOPEN:
            case TASKDONE:
            case TASKDONETITLE:
                suggestions.addAll(createStringSuggestionsMatchingInput(input, filterService.initStepTitles(), FilterPart.VALUE));
                break;
            case PROJECT:
                suggestions.addAll(createStringSuggestionsMatchingInput(input, filterService.initProjects(), FilterPart.VALUE));
                break;
            case PROPERTY:
                suggestions.addAll(createStringSuggestionsMatchingInput(input, filterService.initProcessPropertyTitles(), FilterPart.VALUE));
                break;
            case TASKDONEUSER:
                suggestions.addAll(createUserSuggestionsMatchingInput(input, filterService.initUserList(), FilterPart.VALUE));
            case BATCH:
            case TASKAUTOMATIC:
            case PROCESS:
            case ID:
            case PARENTPROCESSID:
                break;
        }
        return suggestions;
    }

    private List<Suggestion> createSuggestionsForUserCategory(String input) {
        List<String> searchableColumns = Arrays.asList("id:","name:", "surname:", "login:", "ldapLogin:", "active:",
            "deleted:", "location:", "metadataLanguage:", "withMassDownload:", "configProductionDateShow:", "tableSize:",
            "language:");
        return createStringSuggestionsMatchingInput(input, searchableColumns, FilterPart.CATEGORY);
    }

    private List<Suggestion> createStringSuggestionsMatchingInput(String input, List<String> suggestions, FilterPart filterPart) {
        return suggestions.stream()
                .filter(suggestion -> suggestion.startsWith(input))
                .limit(MAX_SUGGESTIONS)
                .map(suggestion -> new Suggestion(input, suggestion, filterPart))
                .collect(Collectors.toList());
    }

    private List<Suggestion> createUserSuggestionsMatchingInput(String input, List<User> suggestions, FilterPart filterPart) {
        return suggestions.stream()
                .filter(user -> user.getLogin().startsWith(input) || user.getFullName().startsWith(input))
                .limit(MAX_SUGGESTIONS)
                .map(user -> new Suggestion(input, user.getLogin(), filterPart))
                .collect(Collectors.toList());
    }

    /**
     * Check if the category passed as String matches a FilterString.
     *
     * @param category as String
     * @return FilterString matching the given String
     */
    private FilterString checkProcessFilterCategory(String category) {
        return Arrays.stream(FilterString.values())
                .filter(f -> f.getFilterGerman().equals(category.toLowerCase()) || f.getFilterEnglish().equals(category.toLowerCase()))
                .findFirst().orElse(null);
    }

    /**
     * Get parsedFilters.
     *
     * @return value of parsedFilters
     */
    public List<ParsedFilter> getParsedFilters() {
        return parsedFilters;
    }

    /**
     * Get filterInEditMode.
     *
     * @return value of filterInEditMode
     */
    public String getFilterInEditMode() {
        return filterInEditMode;
    }

    /**
     * Set filterInEditMode.
     *
     * @param filterInEditMode as java.lang.String
     */
    public void setFilterInEditMode(String filterInEditMode) {
        this.filterInEditMode = filterInEditMode;
    }

    public void addParsedFilter(String plainFilter) {
        parsedFilters.add(new ParsedFilter(plainFilter));
    }

    /**
     * Remove the given filter from the list of ParsedFilters.
     * This method does not take care of the actual plain filter used for filtering.
     *
     * @param filterToBeRemoved as ParsedFilter object
     */
    public void removeParsedFilter(ParsedFilter filterToBeRemoved) {
        parsedFilters.remove(filterToBeRemoved);
    }

    /**
     * Parse the given filter string.
     * The given string is split into separate filters before being transformed into ParsedFilter objects.
     *
     * @param filterString String containing one or multiple filters
     */
    public void parseFilters(String filterString) {
        parsedFilters.clear();
        List<String> plainFilters = List.of(filterString.split(" ?\"| (?=\\|)|(?<=\\|) "));
        for (String plainFilter : plainFilters) {
            if (plainFilter.replaceAll("\\s", "").length() > 0) {
                parsedFilters.add(new ParsedFilter(plainFilter));
            }
        }
    }

    /**
     * Submit the entered filters and apply them.
     */
    public void submitFilters() {
        Map<String, String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        filterInEditMode = params.get("input");
        if (filterInEditMode.length() > 0) {
            addParsedFilter(filterInEditMode);
            filterInEditMode = "";
            updateSuggestions("");
        }
        updateFilters();
    }

    /**
     * Remove the given filter.
     *
     * @param filterToBeRemoved as ParsedFilter object
     */
    public void removeFilter(ParsedFilter filterToBeRemoved) {
        removeParsedFilter(filterToBeRemoved);
        updateFilters();
    }

    /**
     * Remove the filter passed in the request.
     * The filter can be passed as plain filter string from a {@code <p:remoteCommand/>} as request parameter.
     */
    public void removeFilter() {
        Map<String, String> params = FacesContext.getCurrentInstance().getExternalContext().getRequestParameterMap();
        removeFilter(new ParsedFilter(params.get("plainFilter")));
    }

    /**
     * Build plain filter string from parsed filters and set filter in form class.
     */
    public void updateFilters() {
        StringBuilder newFilter = new StringBuilder();
        for (ParsedFilter parsedFilter : parsedFilters) {
            if (newFilter.length() > 0) {
                newFilter.append(" ");
            }
            newFilter.append(parsedFilter.getPlainFilter());
        }
        if (Objects.nonNull(processForm)) {
            processForm.setFilter(newFilter.toString());
        } else if (Objects.nonNull(taskForm)) {
            taskForm.setFilter(newFilter.toString());
        } else if (Objects.nonNull(userForm)) {
            userForm.setFilter(newFilter.toString());
        }
    }
}
