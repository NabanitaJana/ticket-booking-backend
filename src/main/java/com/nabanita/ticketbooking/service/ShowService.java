package com.nabanita.ticketbooking.service;

import com.nabanita.ticketbooking.dto.*;
import com.nabanita.ticketbooking.entity.*;
import com.nabanita.ticketbooking.exception.ApiException;
import com.nabanita.ticketbooking.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShowService {

    private final ShowRepository showRepo;
    private final SeatRepository seatRepo;
    private final SeatHoldService holdService;

    @Transactional
    public Show createShow(CreateShowRequest req) {
        Show show = new Show();
        show.setName(req.name());
        show.setVenue(req.venue());
        show.setStartTime(req.startTime());
        show = showRepo.save(show);

        List<Seat> seats = new ArrayList<>();
        for (int i = 1; i <= req.totalSeats(); i++) {
            Seat s = new Seat();
            s.setShowId(show.getId());
            s.setSeatNumber("S" + i);
            seats.add(s);
        }
        seatRepo.saveAll(seats);
        return show;
    }

    public List<Show> getShows() {
        return showRepo.findAll();
    }

    public List<SeatResponse> getSeats(Long showId) {
        List<Seat> seats = seatRepo.findByShowIdOrderById(showId);
        if (seats.isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Show not found");
        }
        List<String> owners = holdService.owners(showId, seats.stream().map(Seat::getId).toList());
        List<SeatResponse> result = new ArrayList<>();
        for (int i = 0; i < seats.size(); i++) {
            Seat s = seats.get(i);
            boolean held = s.getStatus() == SeatStatus.AVAILABLE && owners.get(i) != null;
            result.add(new SeatResponse(s.getId(), s.getSeatNumber(), held ? "HELD" : s.getStatus().name()));
        }
        return result;
    }
}
