package uz.hosil.platform.farm.web;

/**
 * Request payload to convert a diagnosis record into a field activity.
 */
public record ConvertToActivityRequest(
        String activityType,
        String additionalNotes
) {}
