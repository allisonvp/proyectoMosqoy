package com.example.sw2.controller.gestor;

import com.example.sw2.Dao.StorageServiceDao;
import com.example.sw2.constantes.AsignadosSedesId;
import com.example.sw2.constantes.CustomConstants;
import com.example.sw2.constantes.VentasId;
import com.example.sw2.entity.*;
import com.example.sw2.repository.AsignadosSedesRepository;
import com.example.sw2.repository.InventarioRepository;
import com.example.sw2.repository.UsuariosRepository;
import com.example.sw2.repository.VentasRepository;
import com.example.sw2.utils.CustomMailService;
import com.example.sw2.utils.ExceptionView;
import org.apache.tomcat.util.http.fileupload.impl.SizeLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import javax.mail.MessagingException;
import javax.servlet.http.HttpSession;
import javax.validation.Valid;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

@Controller
@RequestMapping("/gestor/productosDisponibles")

public class ProductosDisponiblesController {

    @Autowired
    InventarioRepository inventarioRepository;
    @Autowired
    VentasRepository ventasRepository;
    @Autowired
    AsignadosSedesRepository asignadosSedesRepository;
    @Autowired
    UsuariosRepository usuariosRepository;
    @Autowired
    StorageServiceDao storageServiceDao;
    @Autowired
    CustomMailService customMailService;

    @GetMapping(value ="")
    public String listarProductosDisponibles(Model model){
        model.addAttribute("listainventario",inventarioRepository.findAll());
        return "gestor/productosDisponibles";
    }


    @GetMapping(value="venta")
    public String ventasDeProductos(@ModelAttribute("venta") Ventas venta, Model model, @RequestParam("x") String x, HttpSession session){
        Optional<Inventario> optionalInventario = inventarioRepository.findById(x);
        if ( optionalInventario.isPresent()){
            venta = new Ventas((Usuarios)session.getAttribute("usuario"),optionalInventario.get());
            model.addAttribute("venta", venta);
            return "gestor/productosDisponiblesForm";
        }
        return "redirect:/gestor/productosDisponibles";
    }

    @ExceptionView(value = "gestor/productosDisponiblesForm", getValue ="gestor/productosDisponiblesForm")
    @PostMapping("/registrarventa")
    public String registrarventa(@ModelAttribute("venta") @Valid Ventas venta, BindingResult bindingResult,
                                 Model model, RedirectAttributes attributes,
                                 HttpSession session,
                                 @RequestParam(name = "foto1", required = false) MultipartFile multipartFile) throws Exception {

        StorageServiceResponse s2;

        Optional<Inventario> optionalInventario = inventarioRepository.findInventarioByCodigoinventarioAndCantidadgestorIsGreaterThan(
                venta.getInventario().getCodigoinventario(),0
        );

        if(!optionalInventario.isPresent()){
            attributes.addFlashAttribute("msg", "Hubo un error");
            return "redirect:/gestor/productosDisponibles";
        } else
            venta.setInventario(optionalInventario.get());

        //Se verifica DNI o RUC
        if (venta.getRucdni().length() != 8 && venta.getRucdni().length() != 11 && venta.getRucdni().length() != 0 ) {
            System.out.println("es incorrecto el dni o ruc");
            bindingResult.rejectValue("rucdni", "error.user", "La Cantidad de dígitos debe ser 8 o 11");
        }

        // Se verifica fecha
        if((venta.getFecha()!=null) && venta.getFecha().isBefore(venta.getInventario().getFechaadquisicion()))
            bindingResult.rejectValue("fecha", "error.user", "La fecha debe ser después de: "+ venta.getInventario().getFechaadquisicion().toString());

        // Se verifica cantidad
        if(venta.getInventario().getCantidadgestor()<venta.getCantidad())
            bindingResult.rejectValue("cantidad", "error.user", "Cantidad mayor a la disponible");

        //Verificar que se ingreso el numerodocumento si la venta esta confirmada
        if (venta.getConfirmado() && venta.getId().validateNumeroDocumento()){
                bindingResult.rejectValue("id.numerodocumento","error.user","Ingrese un numero de documento");
        }

        //Verificar que el número de documento sea único
        if (!bindingResult.hasFieldErrors("id") && venta.getConfirmado() && ventasRepository.findById(venta.getId()).isPresent()) {
                bindingResult.rejectValue("id.numerodocumento", "error.user", "El numero y tipo de documento de esta venta ya ha sido registrada anteriormente");
        }


        if ((bindingResult.hasErrors())) {
            model.addAttribute("venta", venta);
            return "gestor/productosDisponiblesForm";
        }else {
            if(multipartFile!=null && !multipartFile.isEmpty()){
                try {
                    s2 = storageServiceDao.store(venta,multipartFile);
                } catch (IOException e) {
                    s2 = new StorageServiceResponse("error","Error subiendo el archivo");
                }
                if (!s2.isSuccess()) {
                    bindingResult.rejectValue("media", "error.user", s2.getMsg());
                    model.addAttribute("venta", venta);
                    return "gestor/productosDisponiblesForm";
                }
            }
            venta.getInventario().setCantidadgestor(venta.getInventario().getCantidadgestor()-venta.getCantidad());
            venta.getInventario().setCantidadtotal(venta.getInventario().getCantidadtotal()-venta.getCantidad());
            inventarioRepository.save(venta.getInventario());
            venta.setVendedor((Usuarios) session.getAttribute("usuario"));
            ventasRepository.save(venta);
            try {
                customMailService.sendSaleConfirmation(venta);
            } catch (MessagingException | IOException  e) {
                e.printStackTrace();
            }
            attributes.addFlashAttribute("msg", "Venta de producto realizada");
            return "redirect:/gestor/productosDisponibles";
        }
    }


    @GetMapping(value="asignar")
    public String asignarProducto(@ModelAttribute("asignadosSedes") AsignadosSedes asignadosSedes ,Model model, @RequestParam("x") String x){
        Optional<Inventario> optionalInventario = inventarioRepository.findById(x);
        if ( optionalInventario.isPresent()){
            asignadosSedes = new AsignadosSedes(new AsignadosSedesId(null,null,optionalInventario.get(),null,null));
            //Inventario inventario=optionalInventario.get();
            model.addAttribute("asignadosSedes", asignadosSedes);
            model.addAttribute("listasedes",usuariosRepository.findUsuariosByRoles_IdrolesOrderByApellido(3));
            return "gestor/productosDisponiblesAsignar";
        }
        return "redirect:/gestor/productosDisponibles";
    }



    @PostMapping("/resgistrarasignacion")
    public String registrarAsignacionProducto(@ModelAttribute("asignadosSedes") @Valid AsignadosSedes asignadosSedes, BindingResult bindingResult,
                                              Model model, RedirectAttributes attributes,
                                              HttpSession session) {
        Optional<Inventario> optionalInventario = inventarioRepository.findInventarioByCodigoinventarioAndCantidadgestorIsGreaterThan(
                asignadosSedes.getId().getProductoinventario().getCodigoinventario(),0
        );

        Optional<Usuarios> optionalUsuarios = usuariosRepository.findUsuariosByRoles_idrolesAndIdusuarios(3,
                asignadosSedes.getId().getSede().getIdusuarios());

        // Se verifica que el producto de Inventario
        if(!optionalInventario.isPresent()){
            attributes.addFlashAttribute("msg", "Hubo un error");
            return "redirect:/gestor/productosDisponibles";
        } else
            asignadosSedes.getId().setProductoinventario(optionalInventario.get());

        //Se verifica la sede
        if(!optionalUsuarios.isPresent())
            bindingResult.rejectValue("id.sede","error.user","Escoja una sede");
        else
            asignadosSedes.getId().setSede(optionalUsuarios.get());

        //Se verifica la fecha de envio
        if ((asignadosSedes.getFechaenvio()!=null) && asignadosSedes.getFechaenvio().isBefore(asignadosSedes.getId().getProductoinventario().getFechaadquisicion()))
            bindingResult.rejectValue("fechaenvio","error.user","La fecha debe ser después del :"+asignadosSedes.getId().getProductoinventario().getFechaadquisicion().toString());

        //Se verfica el precio de venta
        if (!((asignadosSedes.getId().getPrecioventa()!=null) && asignadosSedes.getId().getPrecioventa()>0.0))
            bindingResult.rejectValue("id.precioventa","error.user","Ingrese un precio válido");

        //Se verifica la cantidad asignada
        if((asignadosSedes.getStock()!=null) && (optionalInventario.get().getCantidadgestor()<asignadosSedes.getStock()))
            bindingResult.rejectValue("stock","error.user","La cantidad asignada es mayor a la cantidad disponible");

        if (bindingResult.hasErrors()) {
            model.addAttribute("asignadosSedes", asignadosSedes);
            model.addAttribute("listasedes",usuariosRepository.findUsuariosByRoles_IdrolesOrderByApellido(3));
            return "gestor/productosDisponiblesAsignar";
        } else {
            asignadosSedes.getId().setGestor((Usuarios)session.getAttribute("usuario"));

            asignadosSedes.getId().getProductoinventario().subtractCantidad(asignadosSedes.getStock());
            inventarioRepository.save(asignadosSedes.getId().getProductoinventario());
            asignadosSedes.getId().setEstadoasignacion(CustomConstants.ESTADO_ENVIADO_A_SEDE);
            asignadosSedes.setCantidadactual(asignadosSedes.getStock());
            Optional<AsignadosSedes> optional = asignadosSedesRepository.findById(asignadosSedes.getId());
            if(optional.isPresent()){
                AsignadosSedes asignadosSedesTemp = optional.get();
                asignadosSedesTemp.setStock(asignadosSedesTemp.getStock()+  asignadosSedes.getStock());
                asignadosSedesRepository.save(asignadosSedesTemp);
            }else{ asignadosSedesRepository.save(asignadosSedes); }
            /*
            inv.setCantidadgestor(inv.getCantidadgestor()-asignadosSedes.getStock());
            inventarioRepository.save(inv);
            Usuarios usuarios = (Usuarios)session.getAttribute("usuario") ;
            Optional<Usuarios> optionalUsuarios = usuariosRepository.findById(sede);
            Usuarios sedes = optionalUsuarios.get();
            asignadosSedes.setId(new AsignadosSedesId(usuarios, sedes, inv,1,precio));
            asignadosSedes.setCantidadactual(asignadosSedes.getStock());
            System.out.println( "La puta fecha sin asignar es " + asignadosSedes.getFechaenvio());
            asignadosSedesRepository.save(asignadosSedes);
*/
            attributes.addFlashAttribute("msg", "Producto asignado exitosamente");
            return "redirect:/gestor/productosDisponibles";
        }
    }


}
