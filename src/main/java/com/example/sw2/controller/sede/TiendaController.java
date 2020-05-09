package com.example.sw2.controller.sede;

import com.example.sw2.entity.Categorias;
import com.example.sw2.entity.Tienda;
import com.example.sw2.repository.TiendaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.validation.Valid;
import java.util.Optional;

@Controller
@RequestMapping("/sede/tienda")
public class TiendaController {

    @Autowired
    TiendaRepository tiendaRepository;

    @GetMapping(value = {"", "/"})
    public String listCat(@ModelAttribute("tienda") Tienda cat, Model model) {
        model.addAttribute("lista", tiendaRepository.findAll());
        return "sede/ListaTiendas";
    }

    @PostMapping("/save")
    public String editCat(@ModelAttribute("tienda") @Valid Tienda tienda,
                          BindingResult bindingResult, RedirectAttributes attr, Model model) {
        if(bindingResult.hasErrors()){
            model.addAttribute("lista", tiendaRepository.findAll());
            model.addAttribute("msg", "ERROR");
            return "sede/ListaTiendas";
        }
        else {
            Optional<Tienda> optionalTienda = tiendaRepository.findById(tienda.getIdtienda());
            if (optionalTienda.isPresent()) {
                attr.addFlashAttribute("msg", "Tienda actualizada exitosamente");
            }
            else {
                attr.addFlashAttribute("msg", "Producto creado exitosamente");
            }
            tiendaRepository.save(tienda);
            return "redirect:/sede/tienda/";
        }

    }

    @GetMapping("/delete")
    public String deleteCat(Model model,
                            @RequestParam("idtienda") int id,
                            RedirectAttributes attr) {

        Optional<Tienda> c = tiendaRepository.findById(id);

        if (c.isPresent()) {
            tiendaRepository.deleteById(id);
            attr.addFlashAttribute("msg","Producto borrado exitosamente");
        }
        return "redirect:/sede/tienda/";
    }

    //Web service
    @ResponseBody
    @GetMapping(value = "/get",produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Optional<Tienda>> getTienda(@RequestParam(value = "id") int id){
        return new ResponseEntity<>(tiendaRepository.findById(id), HttpStatus.OK);
    }



}