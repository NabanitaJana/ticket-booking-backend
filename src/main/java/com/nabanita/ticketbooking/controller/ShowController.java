package com.nabanita.ticketbooking.controller;

import com.nabanita.ticketbooking.dto.*;
import com.nabanita.ticketbooking.entity.Show;
import com.nabanita.ticketbooking.service.ShowService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/shows")
@RequiredArgsConstructor
public class ShowController {

    private final ShowService showService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Show create(@Valid @RequestBody CreateShowRequest req) {
        return showService.createShow(req);
    }

    @GetMapping
    public List<Show> shows() {
        return showService.getShows();
    }

    @GetMapping("/{showId}/seats")
    public List<SeatResponse> seats(@PathVariable Long showId) {
        return showService.getSeats(showId);
    }
}
